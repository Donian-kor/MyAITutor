package com.donian.myaitutor.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.donian.myaitutor.camera.CaptureImageAction
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ui.home.CaptureFilePrefix
import com.donian.myaitutor.ui.home.HomeViewModel
import com.donian.myaitutor.ui.home.deleteQuietly
import java.io.File

/**
 * 카메라 화면 라우트. 촬영/재촬영/확정/뒤로가기 이벤트를 ViewModel로 위임한다.
 */
@Composable
internal fun CameraRoute(
    viewModel: HomeViewModel,
    captureAction: CaptureImageAction?,
    onCaptureActionReady: (CaptureImageAction?) -> Unit,
    onClose: () -> Unit,
    selectedSubject: String
) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsState()

    CameraScreen(
        capturedImage = uiState.cameraCapturedImage,
        captureAction = captureAction,
        cameraError = uiState.cameraError,
        onCaptureActionReady = onCaptureActionReady,
        onCameraError = { viewModel.onCameraError(it) },
        onBack = {
            uiState.cameraCapturedImage?.deleteQuietly()
            viewModel.clearCameraCapturedImage()
            onClose()
        },
        onCapture = {
            val action = captureAction
            if (action == null) {
                viewModel.onCameraError("카메라가 준비되지 않았습니다. 잠시 후 다시 시도해 주세요.")
            } else {
                val imageFile = File(context.cacheDir, "$CaptureFilePrefix${System.currentTimeMillis()}.jpg")
                action(imageFile) { result ->
                    result.onSuccess { viewModel.setCameraCapturedImage(it) }
                        .onFailure { error ->
                            viewModel.onCameraError(error.localizedMessage ?: "촬영 이미지를 처리하지 못했습니다.")
                        }
                }
            }
        },
        onRetake = {
            uiState.cameraCapturedImage?.deleteQuietly()
            viewModel.clearCameraCapturedImage()
        },
        onConfirm = {
            val image = uiState.cameraCapturedImage
            if (image != null) {
                viewModel.confirmCapture(image)
                onClose()
                if (uiState.modelSnapshot.status == ModelDownloadStatus.READY) {
                    viewModel.analyzeImage(image, selectedSubject)
                } else {
                    viewModel.setStatusMessage("사진을 확인했습니다. AI 모델을 설치하면 문제 풀이를 시작할 수 있습니다.")
                }
            }
        }
    )
}