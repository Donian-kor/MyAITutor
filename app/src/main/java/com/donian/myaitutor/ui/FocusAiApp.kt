package com.donian.myaitutor.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.donian.myaitutor.R
import com.donian.myaitutor.camera.CaptureImageAction
import androidx.compose.runtime.collectAsState
import com.donian.myaitutor.model.AppModel
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.model.ModelRuntimeState
import com.donian.myaitutor.ui.camera.CameraRoute
import com.donian.myaitutor.ui.home.HomeScreen
import com.donian.myaitutor.ui.home.HomeViewModel
import com.donian.myaitutor.ui.home.HomeViewModelHost
import com.donian.myaitutor.ui.home.ModelWarmupState
import com.donian.myaitutor.ui.home.NoModelRecoveryDialog
import com.donian.myaitutor.ui.loading.ModelLoadingScreen
import com.donian.myaitutor.ui.loading.resolveModelLoadingPhase
import com.donian.myaitutor.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.donian.myaitutor.ui.home.deleteQuietly
import com.donian.myaitutor.ui.home.deleteStaleCaptures

@Composable
fun FocusAiApp(host: HomeViewModelHost) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // ViewModelStore에 보관되어 회전 시에도 대화/입력 상태가 유지된다.
    val viewModel: HomeViewModel = host.homeViewModel
    val uiState by viewModel.state.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showWifiDownloadWarning by rememberSaveable { mutableStateOf(false) }
    /** 첫 화면(모델 로딩)을 통과했는지 여부. "나중에 하기"도 여기에 반영됩니다. */
    var showMain by rememberSaveable { mutableStateOf(false) }
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
    var previousRuntimeState by remember { mutableStateOf(uiState.runtimeState) }
    var previousSelectedModel by remember { mutableStateOf(uiState.selectedModel) }

    fun requestModelDownload(model: AppModel) {
        if (context.isConnectedToWifi()) {
            viewModel.startModelDownload(model)
        } else {
            showWifiDownloadWarning = true
        }
    }

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

    // 뒤로가기: 앱을 완전히 종료하고 모델 메모리를 즉시 해제합니다.
    BackHandler(enabled = !showCamera && !showSettings) {
        viewModel.close()
        activity?.finishAndRemoveTask()
    }

    LaunchedEffect(uiState.selectedModelSnapshot.status) {
        if (uiState.selectedModelSnapshot.status == ModelDownloadStatus.READY) {
            viewModel.preWarmIfNeeded()
        } else if (uiState.selectedModelSnapshot.status == ModelDownloadStatus.NOT_INSTALLED) {
            viewModel.setStatusMessage(modelDownloadAvailable)
        }
    }

    // 엔진 준비가 끝나면 자동으로 메인 화면으로 이동합니다.
    LaunchedEffect(uiState.warmup) {
        if (uiState.warmup == ModelWarmupState.READY) {
            showMain = true
        }
    }

    LaunchedEffect(uiState.runtimeState, uiState.selectedModel) {
        val switchCompleted = previousRuntimeState == ModelRuntimeState.TRANSITIONING &&
            uiState.runtimeState == ModelRuntimeState.ACTIVE &&
            previousSelectedModel != uiState.selectedModel
        previousRuntimeState = uiState.runtimeState
        previousSelectedModel = uiState.selectedModel

        if (switchCompleted) {
            val modelName = context.getString(
                if (uiState.selectedModel == AppModel.QWEN3) {
                    R.string.model_qwen_name
                } else {
                    R.string.model_gemma_name
                }
            )
            Toast.makeText(
                context,
                context.getString(R.string.model_transition_complete, modelName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val loadingPhase = resolveModelLoadingPhase(uiState.selectedModelSnapshot, uiState.warmup)

    if (!showMain) {
        ModelLoadingScreen(
            phase = loadingPhase,
            snapshot = uiState.selectedModelSnapshot,
            onDownload = { requestModelDownload(uiState.selectedModel) },
            onRetry = {
                if (uiState.selectedModelSnapshot.status == ModelDownloadStatus.READY) {
                    viewModel.retryModelWarmup()
                } else {
                    requestModelDownload(uiState.selectedModel)
                }
            },
            onSkip = { showMain = true }
        )
    } else if (showSettings) {
        SettingsScreen(
            // Gemma/Qwen 카드는 각 모델의 다운로드 스냅샷을 독립적으로 사용한다.
            snapshot = uiState.modelSnapshot,
            backendLabel = uiState.backendLabel,
            thinkingMode = uiState.thinkingMode,
            onThinkingModeChange = { viewModel.setThinkingMode(it) },
            onBack = { showSettings = false },
            onDownload = { requestModelDownload(uiState.selectedModel) },
            onDeleteModel = { viewModel.deleteModelFile(uiState.selectedModel) },
            onClearCache = { viewModel.clearCache() },
            selectedModel = uiState.selectedModel,
            qwenSnapshot = uiState.qwenSnapshot,
            isModelTransitioning = uiState.isModelBusy,
            modelSelectionBlockedReason = uiState.modelSelectionBlockedReason,
            onModelSelected = { viewModel.setSelectedModel(it) },
            onDownloadModel = ::requestModelDownload,
            onDeleteModelFile = { viewModel.deleteModelFile(it) }
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
            modelReady = uiState.selectedModelSnapshot.status == ModelDownloadStatus.READY,
            onOpenSettings = { showSettings = true },
            onOpenCamera = ::openCamera,
            answer = uiState.answer,
            ocrText = uiState.ocrText,
            preprocessedImage = uiState.preprocessedImage,
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
            onSend = {
                viewModel.sendQuestion(uiState.inputText, selectedSubject)
                focusManager.clearFocus()
            },
            onSendChoice = { prompt ->
                viewModel.sendQuestion(
                    prompt,
                    selectedSubject,
                    showChoicesAfterResponse = true
                )
            },
            showFollowUpChoices = uiState.showFollowUpChoices,
            onCancelGeneration = { viewModel.cancelGeneration() },
            selectedGrade = uiState.selectedGrade,
            onGradeSelected = { viewModel.selectGrade(it) },
            chatMode = uiState.chatMode,
            onChatModeChange = { viewModel.setChatMode(it) },
            tutorState = uiState.tutorState,
            onRecoverTutor = { viewModel.recoverTutor() },
            messages = uiState.messages,
            freeChatBlockedReason = uiState.freeChatBlockedReason
        )
    }

    // 어떤 모델도 로드되지 않은 경우 복구 선택지를 제공한다 (계획서 0.4 2단계).
    if (uiState.isNoModelRecoveryRequired) {
        NoModelRecoveryDialog(
            failureMessage = uiState.transitionFailure,
            onRetry = { viewModel.retryModelLoad() },
            onFallbackToGemma = { viewModel.fallbackToGemma() },
            onDismiss = { viewModel.dismissNoModelRecovery() }
        )
    }

    if (uiState.runtimeState == ModelRuntimeState.TRANSITIONING) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 32.dp,
                        vertical = 28.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = stringResource(R.string.model_transition_loading_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.model_transition_loading_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        }
    }

    if (showWifiDownloadWarning) {
        AlertDialog(
            onDismissRequest = { showWifiDownloadWarning = false },
            title = { Text(stringResource(R.string.dialog_wifi_download_title)) },
            text = { Text(stringResource(R.string.dialog_wifi_download_message)) },
            confirmButton = {
                TextButton(onClick = { showWifiDownloadWarning = false }) {
                    Text(stringResource(R.string.btn_confirm))
                }
            }
        )
    }
}

private fun Context.isConnectedToWifi(): Boolean {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
