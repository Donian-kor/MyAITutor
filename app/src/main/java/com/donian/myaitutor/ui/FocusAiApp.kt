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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

private val Subjects = listOf("국어", "영어", "수학", "과학", "사회", "경제", "철학", "정보", "기타")

private sealed interface AnswerSegment {
    data class Text(val value: String) : AnswerSegment
    data class Formula(val latex: String, val display: Boolean, val source: String) : AnswerSegment
}

private val MathDelimiterPattern = Regex(
    """(?s)\$\$(.+?)\$\$|\\\[(.+?)\\\]|\\\((.+?)\\\)|(?<!\\)\$(?!\$)(.+?)(?<!\\)\$(?!\$)"""
)

private fun parseAnswerSegments(answer: String): List<AnswerSegment> = buildList {
    var cursor = 0
    MathDelimiterPattern.findAll(answer).forEach { match ->
        if (match.range.first > cursor) {
            add(AnswerSegment.Text(answer.substring(cursor, match.range.first)))
        }
        val displayLatex = match.groups[1]?.value ?: match.groups[2]?.value
        val inlineLatex = match.groups[3]?.value ?: match.groups[4]?.value
        if (displayLatex != null) {
            add(AnswerSegment.Formula(displayLatex.trim(), display = true, source = match.value))
        } else if (inlineLatex != null) {
            add(AnswerSegment.Formula(inlineLatex.trim(), display = false, source = match.value))
        }
        cursor = match.range.last + 1
    }
    if (cursor < answer.length) add(AnswerSegment.Text(answer.substring(cursor)))
}

@Composable
private fun AssistantAnswer(text: String, modifier: Modifier = Modifier) {
    val textStyle = MaterialTheme.typography.bodyLarge
    val mathConfig = LatexConfig(
        fontSize = textStyle.fontSize,
        theme = LatexTheme.material3()
    )
    val measurer = rememberLatexMeasurer(mathConfig)
    val isDarkTheme = isSystemInDarkTheme()
    val segments = remember(text) { parseAnswerSegments(text) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var index = 0
        while (index < segments.size) {
            val segment = segments[index]
            if (segment is AnswerSegment.Formula && segment.display) {
                LatexAutoWrap(
                    latex = segment.latex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    config = mathConfig,
                    isDarkTheme = isDarkTheme
                )
                index++
            } else {
                val inlineContent = mutableMapOf<String, InlineTextContent>()
                val annotatedText = buildAnnotatedString {
                    while (index < segments.size) {
                        when (val current = segments[index]) {
                            is AnswerSegment.Text -> append(current.value)
                            is AnswerSegment.Formula -> {
                                if (current.display) return@buildAnnotatedString
                                val id = "formula_$index"
                                val rendered = measurer.inlineContent(
                                    latex = current.latex,
                                    config = mathConfig,
                                    isDarkTheme = isDarkTheme
                                )
                                if (rendered == null) {
                                    append(current.source)
                                } else {
                                    appendInlineContent(id, current.latex)
                                    inlineContent[id] = rendered
                                }
                            }
                        }
                        index++
                    }
                }
                Text(
                    text = annotatedText,
                    inlineContent = inlineContent,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
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
    var followUp by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var backendLabel by remember { mutableStateOf("아직 모델이 실행되지 않았습니다.") }
    var capturedImage by remember { mutableStateOf<File?>(null) }
    var cameraCapturedImage by remember { mutableStateOf<File?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (granted) {
            cameraError = null
            showCamera = true
        } else {
            statusMessage = "카메라 권한이 필요하면 문제를 촬영할 수 없습니다."
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
        isAnalyzing = true
        answer = "촬영한 문제를 분석하고 있습니다."
        statusMessage = null
        scope.launch {
            try {
                assistant.analyzeImage(image, subject) { newText ->
                    answer = newText
                }
                backendLabel = "실행 백엔드: ${assistant.backendLabel()}"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                statusMessage = error.localizedMessage ?: "AI 분석 중 오류가 발생했습니다."
            } finally {
                isAnalyzing = false
            }
        }
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
            onSubjectSelected = { selectedSubject = it },
            capturedImage = capturedImage,
            modelReady = modelSnapshot.status == ModelDownloadStatus.READY,
            onOpenSettings = { showSettings = true },
            onOpenCamera = ::openCamera,
            answer = answer,
            followUp = followUp,
            onFollowUpChange = { followUp = it },
            isAnalyzing = isAnalyzing,
            statusMessage = statusMessage,
            onDismissStatus = { statusMessage = null },
            onSendFollowUp = {
                val question = followUp.trim()
                if (question.isNotEmpty() && !isAnalyzing) {
                    if (modelSnapshot.status != ModelDownloadStatus.READY) {
                        statusMessage = "AI 모델을 먼저 설치해 주세요."
                    } else {
                        isAnalyzing = true
                        val previousAnswer = answer
                        answer = "$previousAnswer\n\n학생: $question\n\nAI 답변: "
                        followUp = ""
                        scope.launch {
                            try {
                                assistant.askFollowUp(question) { newText ->
                                    answer = "$previousAnswer\n\n학생: $question\n\nAI 답변: $newText"
                                }
                            } catch (error: Throwable) {
                                statusMessage = error.localizedMessage ?: "추가 질문 처리 중 오류가 발생했습니다."
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun HomeScreen(
    selectedSubject: String,
    onSubjectSelected: (String) -> Unit,
    capturedImage: File?,
    modelReady: Boolean,
    onOpenSettings: () -> Unit,
    onOpenCamera: () -> Unit,
    answer: String,
    followUp: String,
    onFollowUpChange: (String) -> Unit,
    isAnalyzing: Boolean,
    statusMessage: String?,
    onDismissStatus: () -> Unit,
    onSendFollowUp: () -> Unit
) {
    var subjectMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 3.dp
            ) {
                Button(
                    onClick = onOpenCamera,
                    enabled = !isAnalyzing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text("문제 촬영", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "나만의 AI쌤",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    OutlinedButton(
                        onClick = { subjectMenuExpanded = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
                    ) {
                        Text(selectedSubject, maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "과목 선택")
                    }
                    DropdownMenu(
                        expanded = subjectMenuExpanded,
                        onDismissRequest = { subjectMenuExpanded = false }
                    ) {
                        Subjects.forEach { subject ->
                            DropdownMenuItem(
                                text = { Text(subject) },
                                onClick = {
                                    onSubjectSelected(subject)
                                    subjectMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "설정")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isAnalyzing) "문제 분석 중" else "AI 생성기",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    if (answer.isBlank()) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Color(0xFFB48A35),
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (capturedImage == null) "문제를 촬영해 주세요." else "AI 풀이가 여기에 표시됩니다.",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (modelReady) {
                                    "화면 아래의 문제 촬영 버튼을 눌러 시작하세요."
                                } else {
                                    "AI 모델을 설치하면 촬영한 문제를 풀이할 수 있습니다."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (!modelReady) {
                                TextButton(onClick = onOpenSettings) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("AI 모델 설정")
                                }
                            }
                        }
                    } else {
                        SelectionContainer {
                            AssistantAnswer(
                                text = answer,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                    }

                    if (statusMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFFF0E8))
                                .padding(start = 10.dp, top = 5.dp, bottom = 5.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                statusMessage,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8A3C16)
                            )
                            TextButton(onClick = onDismissStatus) { Text("닫기") }
                        }
                    }

                    if (answer.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = followUp,
                                onValueChange = onFollowUpChange,
                                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (followUp.isEmpty()) {
                                            Text(
                                                "답변에 추가 질문하기",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                            IconButton(
                                onClick = onSendFollowUp,
                                enabled = followUp.isNotBlank() && !isAnalyzing
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "질문 전송", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraScreen(
    capturedImage: File?,
    captureAction: CaptureImageAction?,
    cameraError: String?,
    onCaptureActionReady: (CaptureImageAction?) -> Unit,
    onCameraError: (String) -> Unit,
    onBack: () -> Unit,
    onCapture: () -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit
) {
    val view = LocalView.current
    val activity = view.context as? Activity
    val capturedBitmap = remember(capturedImage) {
        capturedImage?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
    }
    val capturedImageTransform = remember(capturedImage) {
        val orientation = capturedImage?.let {
            android.media.ExifInterface(it.absolutePath).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
        } ?: android.media.ExifInterface.ORIENTATION_NORMAL

        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f to 1f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f to 1f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f to 1f
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0f to -1f
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180f to -1f
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> 90f to -1f
            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> 270f to -1f
            else -> 0f to 1f
        }
    }

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false

        onDispose {
            if (previousLightStatusBars != null) {
                controller?.isAppearanceLightStatusBars = previousLightStatusBars
            }
            if (previousLightNavigationBars != null) {
                controller?.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (capturedImage == null) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onCaptureReady = onCaptureActionReady,
                onCameraError = onCameraError
            )
        } else if (capturedBitmap != null) {
            Image(
                bitmap = capturedBitmap,
                contentDescription = "촬영한 문제 미리보기",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = capturedImageTransform.first
                        scaleX = capturedImageTransform.second
                    },
                contentScale = ContentScale.Fit
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "카메라 닫기",
                        tint = Color.White
                    )
                }
                Text(
                    if (capturedImage == null) "문제 촬영" else "사진 확인",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.weight(1f))

            if (cameraError != null) {
                Text(
                    cameraError,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            if (capturedImage == null) {
                Button(
                    onClick = onCapture,
                    enabled = captureAction != null,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 20.dp)
                        .navigationBarsPadding()
                        .size(76.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "사진 촬영",
                        modifier = Modifier.size(30.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("다시 촬영")
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("확인")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    snapshot: ModelDownloadSnapshot,
    backendLabel: String,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onDeleteModel: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isBusy = snapshot.status == ModelDownloadStatus.DOWNLOADING ||
        snapshot.status == ModelDownloadStatus.QUEUED || snapshot.status == ModelDownloadStatus.PAUSED

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "뒤로", modifier = Modifier.size(25.dp))
                }
                Text("설정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gemma 4 E4B-IT", fontWeight = FontWeight.Bold)
                            Text("LiteRT-LM · 온디바이스 멀티모달 AI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("모델 상태", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (snapshot.status) {
                                ModelDownloadStatus.READY -> "설치 완료"
                                ModelDownloadStatus.DOWNLOADING -> "다운로드 중"
                                ModelDownloadStatus.QUEUED -> "대기 중"
                                ModelDownloadStatus.PAUSED -> "일시 중지"
                                ModelDownloadStatus.FAILED -> "오류"
                                ModelDownloadStatus.NOT_INSTALLED -> "미설치"
                            },
                            fontWeight = FontWeight.SemiBold,
                            color = if (snapshot.status == ModelDownloadStatus.READY) Color(0xFF16845B) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(snapshot.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isBusy || snapshot.status == ModelDownloadStatus.READY) {
                        LinearProgressIndicator(
                            progress = { if (snapshot.status == ModelDownloadStatus.READY) 1f else snapshot.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${snapshot.downloadedLabel} / ${snapshot.totalLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (snapshot.status == ModelDownloadStatus.READY) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16845B))
                            Spacer(Modifier.width(7.dp))
                            Text("모델 파일을 찾았습니다. AI 분석을 바로 사용할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                            Text("모델 삭제")
                        }
                    } else {
                        Button(
                            onClick = onDownload,
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (snapshot.status == ModelDownloadStatus.FAILED) "다시 다운로드" else "AI 모델 다운로드")
                        }
                    }
                }
            }

            InfoCard(
                icon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "다운로드 안내",
                body = "모델 파일은 약 3.66GB입니다. 요금이 발생할 수 있으니 반드시 Wi-Fi에서 다운로드하세요. 다운로드가 끝나면 AI 추론은 기기 안에서만 실행됩니다."
            )
            InfoCard(
                icon = { Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "AI 생성기 학습 방식",
                body = "개념 설명 → 핵심 원리 → 문제 적용 → 계산 단계 → 최종 답안 순으로 검산까지 설명합니다. 답변에 추가 질문을 이어서 학습할 수 있습니다."
            )
            InfoCard(
                icon = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "개인정보 오프라인 처리",
                body = "문제 촬영과 분석은 모두 앱에서 처리되어 서버로 저장되지 않습니다. AI 분석 요청과 추가 질문은 로컬 AI 서버로 전송되며 학습합니다. 최초 모델 다운로드 및 업데이트는 인터넷이 필요합니다."
            )
            Text("MyAITutor 0.0.1 · Gemma 4 E4B-IT LiteRT-LM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("실행 환경: 기기 메모리에 맞춰 모델 로딩과 추론이 최적화됩니다.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("현재 백엔드: $backendLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("AI 모델을 삭제할까요?") },
            text = { Text("약 3.66GB의 모델 파일을 기기에서 모두 삭제합니다. 다시 사용하려면 재다운로드가 필요합니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteModel()
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun InfoCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.Top) {
            icon()
            Spacer(Modifier.width(11.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FocusAiTheme(content: @Composable () -> Unit) {
    val colors = androidx.compose.material3.lightColorScheme(
        primary = Color(0xFF245B9B),
        onPrimary = Color.White,
        secondary = Color(0xFF4D6F91),
        background = Color(0xFFF4F6FA),
        surface = Color.White,
        onSurface = Color(0xFF182232),
        onSurfaceVariant = Color(0xFF657386),
        outlineVariant = Color(0xFFDDE3EC)
    )
    MaterialTheme(colorScheme = colors, content = content)
}