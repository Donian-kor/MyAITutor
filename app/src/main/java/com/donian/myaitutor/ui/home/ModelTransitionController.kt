package com.donian.myaitutor.ui.home

import com.donian.myaitutor.R
import com.donian.myaitutor.model.AppModel
import com.donian.myaitutor.model.EngineRuntime
import com.donian.myaitutor.model.ModelDownloadRepository
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.model.ModelRuntimeState
import com.donian.myaitutor.model.ModelSelectionRepository
import com.donian.myaitutor.model.ModelTransitionException
import com.donian.myaitutor.tutor.LlmInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 모델 선택(Gemma ↔ Qwen) 전환과 로드 실패 복구를 담당합니다.
 *
 * 전환은 [EngineRuntime]가 직렬화하며, 이전 모델 자원 해제 후 새 모델을 로드합니다.
 * 실패 시 자동 복구된 경우와 NO_MODEL_LOADED 상태를 [HomeUiState]에 반영합니다.
 */
internal class ModelTransitionController(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<HomeUiState>,
    private val selectionRepository: ModelSelectionRepository,
    private val runtimeCoordinator: EngineRuntime?,
    private val tutorEngine: LlmInferenceEngine,
    private val modelManager: ModelDownloadRepository,
    private val qwenModelManager: ModelDownloadRepository,
    private val stringProvider: StringProvider
) {
    /**
     * 선택 모델을 [target]으로 전환합니다.
     *
     * - 자유 대화 사용 중이면 Qwen 선택을 보류하고 안내를 표시합니다.
     * - 대상 모델 파일이 준비되지 않았으면 전환하지 않습니다(계획서 4.1 0단계).
     * - 실패 시 자동 복구된 경우/NO_MODEL_LOADED 상태를 안내합니다.
     */
    fun setSelectedModel(target: AppModel) {
        if (state.value.selectedModel == target) return
        // 0.6: 자유 대화 사용 중에는 Qwen 선택을 보류한다.
        // 안내만 표시하고 전환하지 않으며, 사용자가 자유 대화를 끄면 선택할 수 있다.
        if (target == AppModel.QWEN3 && state.value.chatMode) {
            state.value = state.value.copy(
                modelSelectionBlockedReason = stringProvider.get(
                    R.string.model_selection_blocked_by_free_chat
                )
            )
            return
        }
        // 대상 모델 파일이 준비되지 않았으면 전환하지 않는다 (계획서 4.1 0단계).
        // 다운로드는 설정 화면의 선택 모델 다운로드 영역에서 진행한다.
        if (snapshotFor(target).status != ModelDownloadStatus.READY) {
            state.value = state.value.copy(
                statusMessage = stringProvider.get(R.string.model_download_required_notice)
            )
            return
        }
        if (state.value.isAnalyzing) {
            tutorEngine.cancelGeneration()
        }

        scope.launch {
            state.value = state.value.copy(runtimeState = ModelRuntimeState.TRANSITIONING)
            val coordinator = runtimeCoordinator
            val result = if (coordinator != null) {
                coordinator.switchTo(target)
            } else {
                // 조정자가 없으면 선택값만 저장하고 런타임 전환은 건너뛴다(테스트 경로).
                selectionRepository.setSelectedModel(target)
                Result.success(tutorEngine)
            }

            result.fold(
                onSuccess = { _ ->
                    selectionRepository.setSelectedModel(target)
                    state.value = state.value.copy(
                        selectedModel = target,
                        selectedModelSnapshot = if (target == AppModel.QWEN3) {
                            qwenModelManager.snapshot()
                        } else {
                            modelManager.snapshot()
                        },
                        runtimeState = ModelRuntimeState.ACTIVE,
                        transitionFailure = null,
                        freeChatBlockedReason = freeChatBlockedReasonFor(target, stringProvider),
                        modelSelectionBlockedReason = null
                    )
                    // Qwen 선택 시 자유 대화는 자동으로 꺼진다.
                    if (target == AppModel.QWEN3 && state.value.chatMode) {
                        selectionRepository.setChatMode(false)
                        state.value = state.value.copy(chatMode = false)
                    }
                },
                onFailure = { error ->
                    val recovered = error is ModelTransitionException && error.recoveredToPrevious
                    val restoredModel = if (recovered) selectionRepository.getSelectedModel() else target
                    state.value = state.value.copy(
                        selectedModel = restoredModel,
                        runtimeState = if (recovered) {
                            ModelRuntimeState.ACTIVE
                        } else {
                            ModelRuntimeState.NO_MODEL_LOADED
                        },
                        transitionFailure = transitionFailureMessage(
                            error,
                            R.string.error_model_transition_failed,
                            stringProvider
                        ),
                        freeChatBlockedReason = freeChatBlockedReasonFor(restoredModel, stringProvider),
                        modelSelectionBlockedReason = modelSelectionBlockedReasonFor(
                            restoredModel,
                            state.value.chatMode,
                            stringProvider
                        )
                    )
                    if (recovered) {
                        state.value = state.value.copy(
                            statusMessage = stringProvider.get(R.string.status_qwen_switch_failed_to_gemma)
                        )
                    }
                }
            )
        }
    }

    /**
     * "다시 시도": 마지막으로 선택했던 모델을 다시 로드합니다.
     *
     * [setSelectedModel]과 달리 대상 모델이 현재 선택값과 같아도 강제로 재시도한다.
     */
    fun retryModelLoad() {
        val target = state.value.selectedModel
        state.value = state.value.copy(
            isNoModelDismissed = false,
            transitionFailure = null
        )
        val coordinator = runtimeCoordinator
        scope.launch {
            state.value = state.value.copy(runtimeState = ModelRuntimeState.TRANSITIONING)
            val result = if (coordinator != null) {
                coordinator.switchTo(target)
            } else {
                Result.success(tutorEngine)
            }
            result.fold(
                onSuccess = { _ ->
                    selectionRepository.setSelectedModel(target)
                    state.value = state.value.copy(
                        selectedModelSnapshot = snapshotFor(target),
                        runtimeState = ModelRuntimeState.ACTIVE,
                        transitionFailure = null,
                        isNoModelDismissed = false,
                        freeChatBlockedReason = freeChatBlockedReasonFor(target, stringProvider)
                    )
                },
                onFailure = { error ->
                    state.value = state.value.copy(
                        runtimeState = ModelRuntimeState.NO_MODEL_LOADED,
                        transitionFailure = transitionFailureMessage(
                            error,
                            R.string.error_model_transition_failed,
                            stringProvider
                        ),
                        isNoModelDismissed = false
                    )
                }
            )
        }
    }

    /**
     * "Gemma로 돌아가기": Gemma 파일이 유효하면 Gemma를 다시 활성화합니다.
     *
     * Gemma 파일도 유효하지 않으면 복구할 수 없으므로 상태 사유만 갱신합니다.
     */
    fun fallbackToGemma() {
        scope.launch {
            state.value = state.value.copy(runtimeState = ModelRuntimeState.TRANSITIONING)
            val coordinator = runtimeCoordinator
            if (coordinator == null) {
                // 조정자가 없는 테스트 경로에서는 선택값만 되돌린다.
                selectionRepository.setSelectedModel(AppModel.GEMMA)
                state.value = state.value.copy(
                    selectedModel = AppModel.GEMMA,
                    selectedModelSnapshot = modelManager.snapshot(),
                    runtimeState = ModelRuntimeState.ACTIVE,
                    transitionFailure = null,
                    isNoModelDismissed = false,
                    freeChatBlockedReason = null,
                    modelSelectionBlockedReason = modelSelectionBlockedReasonFor(
                        AppModel.GEMMA,
                        state.value.chatMode,
                        stringProvider
                    )
                )
                return@launch
            }

            if (modelManager.snapshot().status != ModelDownloadStatus.READY) {
                state.value = state.value.copy(
                    runtimeState = ModelRuntimeState.NO_MODEL_LOADED,
                    transitionFailure = stringProvider.get(R.string.error_no_model_loaded)
                )
                return@launch
            }

            coordinator.switchTo(AppModel.GEMMA).fold(
                onSuccess = { _ ->
                    selectionRepository.setSelectedModel(AppModel.GEMMA)
                    state.value = state.value.copy(
                        selectedModel = AppModel.GEMMA,
                        selectedModelSnapshot = modelManager.snapshot(),
                        runtimeState = ModelRuntimeState.ACTIVE,
                        transitionFailure = null,
                        isNoModelDismissed = false,
                        freeChatBlockedReason = null,
                        modelSelectionBlockedReason = modelSelectionBlockedReasonFor(
                            AppModel.GEMMA,
                            state.value.chatMode,
                            stringProvider
                        )
                    )
                },
                onFailure = { error ->
                    state.value = state.value.copy(
                        runtimeState = ModelRuntimeState.NO_MODEL_LOADED,
                        transitionFailure = transitionFailureMessage(
                            error,
                            R.string.error_no_model_loaded,
                            stringProvider
                        )
                    )
                }
            )
        }
    }

    /** "나중에": 모델 없이 앱을 계속 사용합니다(AI 기능 비활성). */
    fun dismissNoModelRecovery() {
        state.value = state.value.copy(
            isNoModelDismissed = true,
            transitionFailure = null
        )
    }

    /** 모델별 다운로드 스냅샷. Gemma와 Qwen을 각각 독립 관리한다. */
    fun snapshotFor(model: AppModel): ModelDownloadSnapshot =
        if (model == AppModel.QWEN3) qwenModelManager.snapshot() else modelManager.snapshot()
}