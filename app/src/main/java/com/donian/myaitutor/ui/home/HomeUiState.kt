package com.donian.myaitutor.ui.home

import com.donian.myaitutor.ai.DEFAULT_MAX_OUTPUT_TOKENS
import com.donian.myaitutor.ai.MAX_CONTEXT_TOKENS
import com.donian.myaitutor.ai.QwenAssistant
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.model.AppModel
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelRuntimeState
import com.donian.myaitutor.ocr.OcrBlock
import com.donian.myaitutor.tutor.TutorUiState
import java.io.File

enum class ImageAnalysisStage {
    OCR,
    GEMMA
}

/**
 * 앱 시작 시 보여주는 모델 로딩 화면의 엔진 준비 상태.
 *
 * 모델 파일 다운로드 상태([com.donian.myaitutor.model.ModelDownloadStatus])와는 별개로,
 * LiteRT-LM 엔진/Conversation을 실제로 메모리에 올리는 과정을 나타냅니다.
 */
enum class ModelWarmupState {
    /** 아직 워밍업을 시작하지 않았습니다. */
    IDLE,

    /** 엔진과 대화를 준비하는 중입니다. */
    LOADING,

    /** 엔진 준비가 끝나 바로 사용할 수 있습니다. */
    READY,

    /** 엔진 준비에 실패했습니다. */
    FAILED
}

/**
 * 홈 화면의 데이터 상태. Composable은 이 상태를 구독해서 그리기만 합니다.
 * 네비게이션/권한 등 UI-only 상태는 Composer 쪽에 남습니다.
 */
data class HomeUiState(
    val answer: String = "",
    val inputText: String = "",
    val isAnalyzing: Boolean = false,
    val analysisStage: ImageAnalysisStage? = null,
    val ocrText: String = "",
    val ocrBlocks: List<OcrBlock> = emptyList(),
    val statusMessage: String? = null,
    val capturedImage: File? = null,
    val cameraCapturedImage: File? = null,
    val cameraError: String? = null,
    val modelSnapshot: ModelDownloadSnapshot,
    val backendLabel: String = "아직 모델이 실행되지 않았습니다.",
    val showFollowUpChoices: Boolean = false,
    val tokenCount: Int = 0,
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
    /** 학년의 단일 원본. 별도 ViewModel/저장소를 만들지 않습니다. */
    val selectedGrade: TutorGrade = TutorGrade.ELEMENTARY,
    /** 설정 화면에서 사용자가 켜고 끈 Thinking 사용 여부. 학년과 무관합니다. */
    val thinkingMode: Boolean = false,
    /** UI 진행 정보만 담습니다. 해설 원본은 [answer]입니다. */
    val tutorState: TutorUiState = TutorUiState(),
    /** 시작 로딩 화면이 구독하는 엔진 워밍업 상태. */
    val warmup: ModelWarmupState = ModelWarmupState.IDLE,
    /** 크롭 및 이미지 전처리 완료 후 OCR에 전달되기 전의 이미지. */
    val preprocessedImage: File? = null,
    /** 채팅 메시지 목록 (AI 답변 + 학생 질문 분리 표시용). */
    val messages: List<ChatMessage> = emptyList(),
    /** true이면 과목과 무관한 자유 대화 모드입니다. */
    val chatMode: Boolean = false,
    /** 현재 선택된 실행 모델. 저장값이 없으면 GEMMA입니다. */
    val selectedModel: AppModel = AppModel.GEMMA,
    /** 선택 모델의 다운로드 스냅샷. Gemma와 Qwen을 각각 독립 관리합니다. */
    val selectedModelSnapshot: ModelDownloadSnapshot = modelSnapshot,
    /** 활성 모델 런타임 상태. 전환 중에는 입력을 잠급니다. */
    val runtimeState: ModelRuntimeState = ModelRuntimeState.IDLE,
    /** 마지막 전환 실패 사유. null이면 실패 없음. */
    val transitionFailure: String? = null,
    /** 자유 대화를 켤 수 없는 이유. null이면 차단 없음(계획서 0.6). */
    val freeChatBlockedReason: String? = null,
    /** Qwen 선택을 보류해야 하는 이유. null이면 선택 가능(계획서 0.6). */
    val modelSelectionBlockedReason: String? = null,
    /** 사용자가 복구 다이얼로그를 "나중에"로 닫았는지 여부. */
    val isNoModelDismissed: Boolean = false,
    /** Qwen 모델의 다운로드 스냅샷. Gemma [modelSnapshot]과 독립적으로 표시한다. */
    val qwenSnapshot: ModelDownloadSnapshot = modelSnapshot
) {
    /** 전환 중이거나 모델을 사용할 수 없으면 입력을 잠급니다. */
    val isModelBusy: Boolean
        get() = runtimeState == ModelRuntimeState.TRANSITIONING ||
            runtimeState == ModelRuntimeState.NO_MODEL_LOADED

    /** 자유 대화 칩을 사용할 수 있는지 여부. Qwen 활성 시 false입니다. */
    val isFreeChatAvailable: Boolean
        get() = freeChatBlockedReason == null

    /**
     * 선택 모델의 실제 컨텍스트 예산(토큰). 표시용 단일 원본입니다.
     *
     * Gemma는 4,096, Qwen은 2,048이므로 화면 수치가 실제 한도의 2배로 보이지 않게 모델별로 분기합니다.
     */
    val maxContextTokens: Int
        get() = when (selectedModel) {
            AppModel.GEMMA -> MAX_CONTEXT_TOKENS
            AppModel.QWEN3 -> QwenAssistant.QWEN_MAX_CONTEXT_TOKENS
        }

    /**
     * 모델 복구 선택 다이얼로그를 표시해야 하는지 여부(계획서 0.4 2단계).
     *
     * 어떤 모델도 로드되지 않은 NO_MODEL_LOADED 상태에서 사용자가 아직 닫지 않은 경우에만 true다.
     */
    val isNoModelRecoveryRequired: Boolean
        get() = runtimeState == ModelRuntimeState.NO_MODEL_LOADED && !isNoModelDismissed
}
