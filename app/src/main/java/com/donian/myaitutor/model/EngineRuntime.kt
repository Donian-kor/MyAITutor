package com.donian.myaitutor.model

import com.donian.myaitutor.tutor.LlmInferenceEngine
import kotlinx.coroutines.flow.StateFlow

/**
 * 활성 LLM 추론 엔진을 소유하고 전환을 직렬화하는 런타임 계약.
 *
 * [ModelRuntimeCoordinator]가 유일한 프로덕션 구현이며, 테스트에서는 이 인터페이스를
 * 대역으로 주입해 실제 모델 초기화 없이 상태·위임 규칙을 검증한다.
 *
 * 불변식: [RuntimeStatus.activeEngineCount]는 항상 0 또는 1이다.
 */
interface EngineRuntime {

    /** 현재 런타임 상태. UI는 이것을 구독해 전환 중/실패/사용 불가를 표시한다. */
    val status: StateFlow<RuntimeStatus>

    /** 활성 엔진을 반환하고, 없으면 선택된 모델로 생성한다. */
    suspend fun ensureActive(): LlmInferenceEngine

    /** 활성 엔진만 반환한다. 생성하지 않는다. */
    fun getActiveEngine(): LlmInferenceEngine?

    /** 활성 엔진을 해제하고 상태를 IDLE로 되돌린다. */
    suspend fun releaseActive()

    /** 활성 모델을 [target]으로 전환한다. 실패 시 복구 결과를 Result로 알린다. */
    suspend fun switchTo(target: AppModel): Result<LlmInferenceEngine>
}

/** 모델 전환 실패 예외. 사용자에게 안내할 사유와 복구 여부를 담는다. */
class ModelTransitionException(
    val target: AppModel,
    /**
     * true이면 자동 복구로 이전 모델이 다시 활성화되었다.
     * false이면 어떤 모델도 로드되지 않은 NO_MODEL_LOADED 상태다.
     */
    val recoveredToPrevious: Boolean,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)