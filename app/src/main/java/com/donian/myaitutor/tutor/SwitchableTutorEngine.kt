package com.donian.myaitutor.tutor

import com.donian.myaitutor.model.EngineRuntime
import com.donian.myaitutor.model.ModelRuntimeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 현재 활성 모델에 추론 요청을 전달합니다.
 *
 * 모델별 엔진은 [com.donian.myaitutor.model.ModelRuntimeCoordinator]가 소유하며,
 * Gemma 선택 시 기존 [RealGemmaTutorEngine] 인스턴스를 그대로 사용한다.
 * 전환은 이 클래스가 직접 수행하지 않고 [EngineRuntime.switchTo]를 통해서만 일어난다.
 */
class SwitchableTutorEngine(
    private val coordinator: EngineRuntime
) : LlmInferenceEngine {

    override fun stream(request: TutorRequest): Flow<TutorStreamChunk> = flow {
        val engine = coordinator.ensureActive()
        engine.stream(request).collect { emit(it) }
    }

    override fun cancelGeneration() {
        coordinator.getActiveEngine()?.cancelGeneration()
    }

    override suspend fun awaitGenerationStopped(timeoutMillis: Long): Boolean =
        coordinator.getActiveEngine()?.awaitGenerationStopped(timeoutMillis) ?: true

    override suspend fun resetConversation(systemInstruction: String) {
        val engine = coordinator.getActiveEngine() ?: return
        // 활성 상태가 아니면 대화를 교체하지 않는다. 전환 중 호출은 무시된다.
        if (coordinator.status.value.state != ModelRuntimeState.ACTIVE) return
        engine.resetConversation(systemInstruction)
    }

    override suspend fun backendLabel(): String =
        coordinator.getActiveEngine()?.backendLabel() ?: "미실행"

    override suspend fun getTokenCount(): Int =
        coordinator.getActiveEngine()?.getTokenCount() ?: 0

    override suspend fun preWarm() {
        coordinator.ensureActive()
    }

    override suspend fun release() {
        coordinator.releaseActive()
    }
}