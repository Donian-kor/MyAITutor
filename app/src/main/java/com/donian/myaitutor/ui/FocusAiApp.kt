package com.donian.myaitutor.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.content.ContextCompat
import com.donian.myaitutor.camera.CaptureImageAction
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ui.camera.CameraScreen
import com.donian.myaitutor.ui.home.HomeScreen
import com.donian.myaitutor.ui.home.HomeViewModel
import com.donian.myaitutor.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File






/** 촬영 임시 파일 이름 접두사. cacheDir 정리 대상 식별에 사용합니다. */
private const val CaptureFilePrefix = "focus_capture_"

/** 아직 남아 있는 촬영 임시 파일 중 1시간이 지난 것만 지웁니다. 사용 중인 파일은 건드리지 않습니다. */
private fun File.deleteStaleCaptures(now: Long = System.currentTimeMillis()) {
    runCatching {
        listFiles { file -> file.name.startsWith(CaptureFilePrefix) }?.forEach { file ->
            if (now - file.lastModified() > 60L * 60L * 1000L) file.delete()
        }
    }
}

/** 촬영 임시 파일을 지웁니다. 이미 없거나 삭제에 실패해도 예외를 던지지 않습니다. */
private fun File.deleteQuietly() {
    runCatching { if (exists()) delete() }
}


@Composable
fun FocusAiApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: HomeViewModel = remember(scope) {
        HomeViewModel(
            appContext = context.applicationContext,
            scope = scope
        )
    }
    val uiState by viewModel.state.collectAsState()
    DisposableEffect(viewModel) {
        onDispose { /* scope is managed by composition */ }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var selectedSubject by remember { mutableStateOf("수학") }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var captureAction by remember { mutableStateOf<CaptureImageAction?>(null) }
    val focusManager = LocalFocusManager.current

    // 이전 실행에서 남은 촬영 임시 파일을 정리합니다. (C-02)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { context.cacheDir.deleteStaleCaptures() }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (granted) {
            showCamera = true
        } else {
            viewModel.setStatusMessage("카메라 권한이 없으면 문제를 촬영할 수 없습니다. 기기 설정에서 카메라 권한을 허용해 주세요.")
        }
    }

    fun openCamera() {
        if (cameraGranted) {
            showCamera = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(enabled = showCamera) {
        showCamera = false
        uiState.cameraCapturedImage?.deleteQuietly()
        viewModel.clearCameraCapturedImage()
    }

    LaunchedEffect(uiState.modelSnapshot.status) {
        if (uiState.modelSnapshot.status == ModelDownloadStatus.READY) {
            viewModel.preWarmIfNeeded()
        } else if (uiState.modelSnapshot.status == ModelDownloadStatus.NOT_INSTALLED) {
            viewModel.setStatusMessage("AI 모델을 다운로드하면 촬영한 문제를 풀이할 수 있습니다.")
        }
    }

    if (showSettings) {
        SettingsScreen(
            snapshot = uiState.modelSnapshot,
            backendLabel = uiState.backendLabel,
            onBack = { showSettings = false },
            onDownload = { viewModel.startDownload() },
            onDeleteModel = { viewModel.deleteModel() }
        )
    } else if (showCamera) {
        CameraScreen(
            capturedImage = uiState.cameraCapturedImage,
            captureAction = captureAction,
            cameraError = uiState.cameraError,
            onCaptureActionReady = { captureAction = it },
            onCameraError = { viewModel.onCameraError(it) },
            onBack = {
                showCamera = false
                uiState.cameraCapturedImage?.deleteQuietly()
                viewModel.clearCameraCapturedImage()
            },
            onCapture = {
                val action = captureAction
                if (action == null) {
                    viewModel.onCameraError("카메라가 준비되지 않았습니다. 잠시 후 다시 시도해 주세요.")
                } else {
                    val imageFile = File(context.cacheDir, "focus_capture_${System.currentTimeMillis()}.jpg")
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
                    showCamera = false
                    if (uiState.modelSnapshot.status == ModelDownloadStatus.READY) {
                        viewModel.analyzeImage(image, selectedSubject)
                    } else {
                        viewModel.setStatusMessage("사진을 확인했습니다. AI 모델을 설치하면 문제 풀이를 시작할 수 있습니다.")
                    }
                }
            }
        )
    } else {
        HomeScreen(
            selectedSubject = selectedSubject,
            onSubjectSelected = {
                if (selectedSubject != it) {
                    selectedSubject = it
                    viewModel.resetForNewSubject()
                }
            },
            capturedImage = uiState.capturedImage,
            modelReady = uiState.modelSnapshot.status == ModelDownloadStatus.READY,
            onOpenSettings = { showSettings = true },
            onOpenCamera = ::openCamera,
            answer = uiState.answer,
            inputText = uiState.inputText,
            onInputChange = { viewModel.onInputChange(it) },
            isAnalyzing = uiState.isAnalyzing,
            statusMessage = uiState.statusMessage,
            onDismissStatus = { viewModel.dismissStatus() },
            onRetryAnalysis = {
                uiState.capturedImage?.let { viewModel.analyzeImage(it, selectedSubject) }
            },
            canRetryAnalysis = uiState.capturedImage != null && !uiState.isAnalyzing,
            onSend = { viewModel.sendQuestion(uiState.inputText); focusManager.clearFocus() },
            onSendChoice = { prompt -> viewModel.sendQuestion(prompt, showChoicesAfterResponse = true) },
            showFollowUpChoices = uiState.showFollowUpChoices,
            onCancelGeneration = { viewModel.cancelGeneration() }
        )
    }
}
