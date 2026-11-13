package com.donian.myaitutor.ui.home

import com.donian.myaitutor.ai.DEFAULT_MAX_OUTPUT_TOKENS
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.ocr.OcrBlock
import com.donian.myaitutor.tutor.TutorUiState
import java.io.File

enum class ImageAnalysisStage {
    OCR,
    GEMMA
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
    val maxContextTokens: Int = 8000,
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
    /** 학년의 단일 원본. 별도 ViewModel/저장소를 만들지 않습니다. */
    val selectedGrade: TutorGrade = TutorGrade.ELEMENTARY,
    /** UI 진행 정보만 담습니다. 해설 원본은 [answer]입니다. */
    val tutorState: TutorUiState = TutorUiState()
)
