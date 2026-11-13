package com.donian.myaitutor.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.donian.myaitutor.R
import com.donian.myaitutor.camera.CaptureImageAction
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ui.camera.CameraRoute
import com.donian.myaitutor.ui.home.HomeScreen
import com.donian.myaitutor.ui.home.HomeViewModel
import com.donian.myaitutor.ui.home.HomeViewModelHost
import com.donian.myaitutor.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.donian.myaitutor.ui.home.deleteQuietly
import com.donian.myaitutor.ui.home.deleteStaleCaptures

@Composable
fun FocusAiApp() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // ViewModelStore에 보관되어 회전 시에도 대화/입력 상태가 유지된다.
    val host: HomeViewModelHost = viewModel()
    val viewModel: HomeViewModel = host.homeViewModel
    val uiState by viewModel.state.collectAsState()

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
    val cameraPermissionDenied = stringResource(R.string.status_camera_permission_denied)
    val cameraPermissionBlocked = stringResource(R.string.status_camera_permission_blocked)
    val modelDownloadAvailable = stringResource(R.string.status_model_download_available)

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
            // "다시 묻지 않음"으로 영구 거부된 경우에는 안내 문구를 구분한다. (R-06)
            val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CAMERA
            )
            viewModel.setStatusMessage(
                if (canAskAgain) cameraPermissionDenied else cameraPermissionBlocked
            )
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
            viewModel.setStatusMessage(modelDownloadAvailable)
        }
    }

    if (showSettings) {
        SettingsScreen(
            snapshot = uiState.modelSnapshot,
            backendLabel = uiState.backendLabel,
            onBack = { showSettings = false },
            onDownload = { viewModel.startDownload() },
            onDeleteModel = { viewModel.deleteModel() },
            onClearCache = { viewModel.clearCache() }
        )
    } else if (showCamera) {
        CameraRoute(
            viewModel = viewModel,
            captureAction = captureAction,
            onCaptureActionReady = { captureAction = it },
            onClose = { showCamera = false },
            selectedSubject = selectedSubject
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
            ocrText = uiState.ocrText,
            inputText = uiState.inputText,
            onInputChange = { viewModel.onInputChange(it) },
            isAnalyzing = uiState.isAnalyzing,
            analysisStage = uiState.analysisStage,
            tokenCount = uiState.tokenCount,
            maxContextTokens = uiState.maxContextTokens,
            statusMessage = uiState.statusMessage,
            onDismissStatus = { viewModel.dismissStatus() },
            onRetryAnalysis = {
                uiState.capturedImage?.let { viewModel.analyzeImage(it, selectedSubject) }
            },
            canRetryAnalysis = uiState.capturedImage != null && !uiState.isAnalyzing,
            onSend = { viewModel.sendQuestion(uiState.inputText); focusManager.clearFocus() },
            onSendChoice = { prompt -> viewModel.sendQuestion(prompt, showChoicesAfterResponse = true) },
            showFollowUpChoices = uiState.showFollowUpChoices,
            onCancelGeneration = { viewModel.cancelGeneration() },
            selectedGrade = uiState.selectedGrade,
            onGradeSelected = { viewModel.selectGrade(it) },
            tutorState = uiState.tutorState,
            onRecoverTutor = { viewModel.recoverTutor() }
        )
    }
}
