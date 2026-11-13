package com.donian.myaitutor.model

/**
 * 활성 모델 런타임 상태.
 *
 * 전환은 `IDLE`/`ACTIVE` → [TRANSITIONING] → `ACTIVE` 또는 [NO_MODEL_LOADED] 순서로만 전이한다.
 * [NO_MODEL_LOADED]에서는 AI 기능을 사용할 수 없으며 사용자에게 오류 상태를 안내한다.
 */
enum class ModelRuntimeState {
    /** 아직 어떤 모델도 로드하지 않았다. */
    IDLE,

    /** 모델 전환이 진행 중이다. 입력 잠금 대상이며 활성 엔진이 0개인 구간이 반드시 포함된다. */
    TRANSITIONING,

    /** 모델이 정상적으로 로드되어 추론할 수 있다. */
    ACTIVE,

    /** 어떤 모델도 로드되지 않았다. 재시도 또는 다른 모델 선택이 필요하다. */
    NO_MODEL_LOADED;

    /** 추론을 시도할 수 있는 상태인지 여부. */
    val isUsable: Boolean get() = this == ACTIVE
}

/**
 * 런타임 상태 스냅샷.
 *
 * @param activeEngineCount 메모리에 로드된 LLM Engine 수. 항상 0 또는 1이다.
 * @param failureMessage 마지막 전환 실패 사유. 사용자 안내에 사용하며 OCR 원문·질문을 담지 않는다.
 */
data class RuntimeStatus(
    val state: ModelRuntimeState = ModelRuntimeState.IDLE,
    val activeEngineCount: Int = 0,
    val activeModel: AppModel = AppModel.GEMMA,
    val failureMessage: String? = null
) {
    init {
        require(activeEngineCount in 0..1) { "activeEngineCount must be 0 or 1" }
        // 활성 상태인데 엔진이 없거나, 전환 중에 엔진이 1개인 것은 불변식 위반이다.
        require(state != ModelRuntimeState.ACTIVE || activeEngineCount == 1) {
            "ACTIVE 상태에는 활성 엔진이 1개 있어야 합니다."
        }
        require(state != ModelRuntimeState.TRANSITIONING || activeEngineCount == 0) {
            "TRANSITIONING 상태에는 활성 엔진이 없어야 합니다."
        }
    }
}