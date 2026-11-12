package com.donian.myaitutor.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.hrm.latex.renderer.LatexAutoWrap
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme
import androidx.compose.foundation.isSystemInDarkTheme
import com.donian.myaitutor.ai.GemmaAssistant
import com.donian.myaitutor.camera.CameraPreview
import com.donian.myaitutor.camera.CaptureImageAction
import com.donian.myaitutor.model.ModelDownloadManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ui.camera.CameraScreen
import com.donian.myaitutor.ui.home.HomeScreen
import com.donian.myaitutor.ui.home.HomeViewModel
import com.donian.myaitutor.ui.settings.SettingsScreen
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.ui.components.InfoCard
import com.donian.myaitutor.ui.home.FollowUpChoice
import com.donian.myaitutor.ui.home.FollowUpChoices
import com.donian.myaitutor.ui.home.Subjects
import com.donian.myaitutor.ui.text.AssistantAnswer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val viewModel: HomeViewModel = viewModel()
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
