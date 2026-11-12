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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ui.camera.CameraScreen
import com.donian.myaitutor.ui.home.HomeScreen
import com.donian.myaitutor.ui.settings.SettingsScreen
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
    val scope = rememberCoroutineScope()
    val modelManager = remember(context) { ModelDownloadManager(context) }
    val assistant = remember(context) { GemmaAssistant(context) }

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
    var modelSnapshot by remember { mutableStateOf(modelManager.snapshot()) }
    var answer by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var showFollowUpChoices by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var generationId by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var backendLabel by remember { mutableStateOf("아직 모델이 실행되지 않았습니다.") }
    var capturedImage by remember { mutableStateOf<File?>(null) }
    var cameraCapturedImage by remember { mutableStateOf<File?>(null) }
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
            cameraError = null
            showCamera = true
        } else {
            statusMessage = "카메라 권한이 없으면 문제를 촬영할 수 없습니다. 기기 설정에서 카메라 권한을 허용해 주세요."
        }
    }

    fun openCamera() {
        statusMessage = null
        cameraError = null
        cameraCapturedImage = null
        if (cameraGranted) {
            showCamera = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun analyzeImage(image: File, subject: String) {
        if (isAnalyzing) return
        val currentGeneration = ++generationId
        isAnalyzing = true
        answer = "촬영한 문제를 분석하고 있습니다."
        showFollowUpChoices = false
        statusMessage = null
        generationJob = scope.launch {
            var completed = false
            var receivedAnswer = false
            try {
                assistant.analyzeImage(image, subject) { newText ->
                    if (currentGeneration == generationId) {
                        answer = newText
                        receivedAnswer = newText.isNotBlank()
                    }
                }
                if (currentGeneration == generationId) {
                    backendLabel = "실행 백엔드: ${assistant.backendLabel()}"
                    completed = true
                    if (!receivedAnswer) {
                        answer = "AI 답변을 받지 못했습니다. 다시 촬영해 분석을 시도해 주세요."
                        statusMessage = "문제 분석 결과가 비어 있어 선택지를 표시하지 않았습니다."
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (currentGeneration == generationId) {
                    statusMessage = error.localizedMessage ?: "AI 분석 중 오류가 발생했습니다."
                }
            } finally {
                // 분석이 끝나면(성공·실패·중단 모두) 이 작업이 만든 임시 파일은 더 이상 필요 없습니다. (C-02)
                image.deleteQuietly()
                if (currentGeneration == generationId) {
                    isAnalyzing = false
                    showFollowUpChoices = completed && receivedAnswer
                    generationJob = null
                }
            }
        }
    }

    fun sendQuestion(question: String, showChoicesAfterResponse: Boolean = false) {
        if (question.isBlank() || isAnalyzing) return
        if (modelSnapshot.status != ModelDownloadStatus.READY) {
            statusMessage = "AI 모델을 먼저 설치해 주세요."
            return
        }

        val currentGeneration = ++generationId
        val previousAnswer = answer
        val pendingAnswer = if (previousAnswer.isBlank()) {
            "학생: $question\n\nAI 답변: "
        } else {
            "$previousAnswer\n\n학생: $question\n\nAI 답변: "
        }
        isAnalyzing = true
        showFollowUpChoices = false
        answer = pendingAnswer
        inputText = ""
        statusMessage = null
        focusManager.clearFocus()
        generationJob = scope.launch {
            var completed = false
            try {
                assistant.askFollowUp(question) { newText ->
                    if (currentGeneration == generationId) answer = pendingAnswer + newText
                }
                completed = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (currentGeneration == generationId) {
                    statusMessage = error.localizedMessage ?: "질문 처리 중 오류가 발생했습니다."
                }
            } finally {
                if (currentGeneration == generationId) {
                    isAnalyzing = false
                    showFollowUpChoices = completed && showChoicesAfterResponse
                    generationJob = null
                }
            }
        }
    }

    fun cancelGeneration() {
        // 네이티브 취소 플래그를 먼저 세팅한 뒤 코루틴을 취소해야
        // 런타임 스레드의 생성 루프가 즉시 멈춥니다.
        assistant.cancelGeneration()
        generationJob?.cancel()
        generationJob = null
        // 늦게 도착하는 마지막 토큰이 중단 표시를 덮어쓰지 않도록 세대를 무효화합니다.
        generationId++
        isAnalyzing = false
        answer = when {
            answer.endsWith("AI 답변: ") ->
                answer.removeSuffix("AI 답변: ").trimEnd() + "\n\n(답변이 중단되었습니다.)"
            answer.isBlank() || answer == "촬영한 문제를 분석하고 있습니다." -> ""
            else -> answer.trimEnd() + "\n\n(답변이 중단되었습니다.)"
        }
        statusMessage = "AI 답변을 중단했습니다."
    }

    BackHandler(enabled = showCamera) {
        showCamera = false
        cameraCapturedImage = null
        cameraError = null
    }

    LaunchedEffect(modelSnapshot.status) {
        if (modelSnapshot.status == ModelDownloadStatus.READY) {
            scope.launch {
                assistant.preWarm()
                backendLabel = "실행 백엔드: ${assistant.backendLabel()}"
            }
        }
    }

    if (showSettings) {
        SettingsScreen(
            snapshot = modelSnapshot,
            backendLabel = backendLabel,
            onBack = { showSettings = false },
            onDownload = {
                runCatching { modelManager.startDownload() }
                    .onSuccess { statusMessage = "Wi-Fi에서 AI 모델 다운로드가 시작됩니다." }
                    .onFailure { statusMessage = it.localizedMessage ?: "다운로드를 시작하지 못했습니다." }
                modelSnapshot = modelManager.snapshot()
            },
            onDeleteModel = {
                scope.launch {
                    assistant.release()
                    modelManager.deleteModel()
                    modelSnapshot = modelManager.snapshot()
                    backendLabel = "모델이 모두 삭제되었습니다."
                }
            }
        )
    } else if (showCamera) {
        CameraScreen(
            capturedImage = cameraCapturedImage,
            captureAction = captureAction,
            cameraError = cameraError,
            onCaptureActionReady = { captureAction = it },
            onCameraError = { cameraError = it },
            onBack = {
                showCamera = false
                cameraCapturedImage?.deleteQuietly()
                cameraCapturedImage = null
                cameraError = null
            },
            onCapture = {
                val action = captureAction
                if (action == null) {
                    cameraError = "카메라가 준비되지 않았습니다. 잠시 후 다시 시도해 주세요."
                } else {
                    val imageFile = File(context.cacheDir, "focus_capture_${System.currentTimeMillis()}.jpg")
                    action(imageFile) { result ->
                        result.onSuccess { cameraCapturedImage = it }
                            .onFailure { error ->
                                cameraError = error.localizedMessage ?: "촬영 이미지를 처리하지 못했습니다."
                            }
                    }
                }
            },
            onRetake = {
                cameraCapturedImage?.deleteQuietly()
                cameraCapturedImage = null
                cameraError = null
            },
            onConfirm = {
                val image = cameraCapturedImage
                if (image != null) {
                    capturedImage = image
                    cameraCapturedImage = null
                    showCamera = false
                    cameraError = null
                    if (modelSnapshot.status == ModelDownloadStatus.READY) {
                        analyzeImage(image, selectedSubject)
                    } else {
                        answer = "사진을 확인했습니다. AI 모델을 설치하면 문제 풀이를 시작할 수 있습니다."
                        statusMessage = "설정에서 Gemma 4 E4B 모델을 다운로드해 주세요."
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
                    capturedImage = null
                    answer = ""
                    inputText = ""
                    showFollowUpChoices = false
                    statusMessage = null
                }
            },
            capturedImage = capturedImage,
            modelReady = modelSnapshot.status == ModelDownloadStatus.READY,
            onOpenSettings = { showSettings = true },
            onOpenCamera = ::openCamera,
            answer = answer,
            inputText = inputText,
            onInputChange = { inputText = it },
            isAnalyzing = isAnalyzing,
            statusMessage = statusMessage,
            onDismissStatus = { statusMessage = null },
            onSend = { sendQuestion(inputText) },
            onSendChoice = { sendQuestion(it, showChoicesAfterResponse = true) },
            showFollowUpChoices = showFollowUpChoices,
            onCancelGeneration = ::cancelGeneration
        )
    }
}




