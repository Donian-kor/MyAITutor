package com.donian.myaitutor.model

import android.content.Context
import com.donian.myaitutor.ai.GemmaAssistant
import com.donian.myaitutor.ai.QwenAssistant
import com.donian.myaitutor.tutor.LlmInferenceEngine
import com.donian.myaitutor.tutor.QwenTutorEngine
import com.donian.myaitutor.tutor.RealGemmaTutorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gemma/Qwen 런타임을 생성·전환·해제한다. [EngineRuntime]의 유일한 프로덕션 구현이다.
 *
 * 활성 LLM Engine은 항상 0 또는 1개다. 전환은 [mutex]로 직렬화하며,
 * 이전 모델의 추론 종료와 Conversation/Engine 해제가 끝난 뒤에만 새 Engine을 생성한다.
 *
 * 전환 순서 (계획서 0.4):
 * 취소 요청 → 추론 종료 대기 → Conversation.close → Engine.close → 상태 확인 → 신규 Engine 생성
 */
class ModelRuntimeCoordinator(
    private val appContext: Context,
    private val selectionRepository: ModelSelectionRepository =
        PrefsModelSelectionRepository(appContext),
    /** 취소 확인 대기 시간. [GemmaAssistant]의 취소 확인 타임아웃과 같은 값을 기본 사용한다. */
    private val cancelConfirmTimeoutMillis: Long = CANCEL_CONFIRM_TIMEOUT_MS
) : EngineRuntime {

    private val mutex = Mutex()
    private var activeEngine: LlmInferenceEngine? = null

    private val _status = MutableStateFlow(
        RuntimeStatus(
            state = ModelRuntimeState.IDLE,
            activeEngineCount = 0,
            activeModel = selectionRepository.getSelectedModel()
        )
    )

    override val status: StateFlow<RuntimeStatus> = _status.asStateFlow()

    /** 메모리에 로드된 LLM Engine 수. 항상 0 또는 1이다. */
    val activeEngineCount: Int get() = if (activeEngine == null) 0 else 1

    fun getActiveModel(): AppModel = _status.value.activeModel

    /** 전환이 진행 중인지 여부. 홈 화면 입력 잠금에 사용한다. */
    fun isTransitioning(): Boolean = _status.value.state == ModelRuntimeState.TRANSITIONING

    override fun getActiveEngine(): LlmInferenceEngine? = activeEngine

    override suspend fun ensureActive(): LlmInferenceEngine = mutex.withLock {
        val current = activeEngine
        if (current != null && _status.value.state == ModelRuntimeState.ACTIVE) return current
        // 재시도 경로: NO_MODEL_LOADED 상태에서도 동일하게 새 엔진을 생성한다.
        val loaded = createActiveEngine(_status.value.activeModel)
        activeEngine = loaded.engine
        _status.value = loaded.status
        loaded.engine
    }

    /**
     * 활성 모델을 [target]으로 전환한다.
     *
     * - 활성 모델이 있으면 추론 종료 → Conversation/Engine 해제 후 새 모델을 로드한다.
     * - 실패 시 자동으로 이전 모델을 재로드하고, 그것도 실패하면 NO_MODEL_LOADED로 전환한다.
     *
     * @return 성공 시 새 엔진. 실패 시 [ModelTransitionException]으로 복구 여부를 알린다.
     */
    override suspend fun switchTo(target: AppModel): Result<LlmInferenceEngine> = mutex.withLock {
        val previousModel = _status.value.activeModel
        val previousEngine = activeEngine

        if (previousEngine != null) {
            // 전환 시작: 활성 엔진을 0으로 만들고 TRANSITIONING 상태로 전이한다.
            _status.value = RuntimeStatus(
                state = ModelRuntimeState.TRANSITIONING,
                activeEngineCount = 0,
                activeModel = previousModel
            )
            // 1) 추론 중이면 취소를 요청하고 네이티브 추론 종료를 기다린다.
            previousEngine.cancelGeneration()
            previousEngine.awaitGenerationStopped(cancelConfirmTimeoutMillis)
            // 2) 이전 Conversation/Engine을 해제한다.
            previousEngine.release()
            activeEngine = null
        }

        return try {
            // 3) 이전 모델 자원이 모두 내려간 뒤 새 모델을 로드한다.
            val loaded = createActiveEngine(target)
            activeEngine = loaded.engine
            _status.value = loaded.status
            selectionRepository.setSelectedModel(target)
            Result.success(loaded.engine)
        } catch (error: Exception) {
            // 4) 자동 복구: 이전 모델을 재로드한다. 실패하면 NO_MODEL_LOADED로 전환한다.
            val restored = runCatching { createActiveEngine(previousModel) }
            restored.fold(
                onSuccess = { previous ->
                    activeEngine = previous.engine
                    _status.value = previous.status.copy(
                        failureMessage = error.message ?: "모델 전환 실패"
                    )
                    selectionRepository.setSelectedModel(previousModel)
                    Result.failure(
                        ModelTransitionException(
                            target = target,
                            recoveredToPrevious = true,
                            message = error.message ?: "모델 전환 실패",
                            cause = error
                        )
                    )
                },
                onFailure = { fallbackError ->
                    activeEngine = null
                    _status.value = RuntimeStatus(
                        state = ModelRuntimeState.NO_MODEL_LOADED,
                        activeEngineCount = 0,
                        activeModel = previousModel,
                        failureMessage = fallbackError.message
                            ?: error.message
                            ?: "모델 전환 실패"
                    )
                    Result.failure(
                        ModelTransitionException(
                            target = target,
                            recoveredToPrevious = false,
                            message = "모델을 로드하지 못했습니다.",
                            cause = fallbackError
                        )
                    )
                }
            )
        }
    }

    override suspend fun releaseActive() = mutex.withLock {
        val current = activeEngine ?: return@withLock
        current.cancelGeneration()
        current.awaitGenerationStopped(cancelConfirmTimeoutMillis)
        current.release()
        activeEngine = null
        _status.value = RuntimeStatus(
            state = ModelRuntimeState.IDLE,
            activeEngineCount = 0,
            activeModel = _status.value.activeModel
        )
    }

    private suspend fun createActiveEngine(model: AppModel): LoadedEngine {
        val engine = createEngine(model)
        return try {
            engine.preWarm()
            LoadedEngine(
                engine = engine,
                status = RuntimeStatus(
                    state = ModelRuntimeState.ACTIVE,
                    activeEngineCount = 1,
                    activeModel = model
                )
            )
        } catch (error: Exception) {
            runCatching { engine.release() }
            throw error
        }
    }

    private fun createEngine(model: AppModel): LlmInferenceEngine = when (model) {
        AppModel.GEMMA -> RealGemmaTutorEngine(GemmaAssistant(appContext))
        AppModel.QWEN3 -> QwenTutorEngine(QwenAssistant(appContext))
    }

    private data class LoadedEngine(
        val engine: LlmInferenceEngine,
        val status: RuntimeStatus
    )

    companion object {
        /** 취소 확인 대기 시간. */
        const val CANCEL_CONFIRM_TIMEOUT_MS = 15_000L
    }
}