package com.donian.myaitutor.ui.home

import com.donian.myaitutor.model.ModelDownloadSnapshot
import java.io.File

/**
 * 홈 화면의 데이터 상태. Composable은 이 상태를 구독해서 그리기만 합니다.
 * 네비게이션/권한 등 UI-only 상태는 Composable 쪽에 남습니다.
 */
data class HomeUiState(
    val answer: String = "",
    val inputText: String = "",
    val isAnalyzing: Boolean = false,
    val statusMessage: String? = null,
    val capturedImage: File? = null,
    val cameraCapturedImage: File? = null,
    val cameraError: String? = null,
    val modelSnapshot: ModelDownloadSnapshot,
    val backendLabel: String = "아직 모델이 실행되지 않았습니다.",
    val showFollowUpChoices: Boolean = false
)
