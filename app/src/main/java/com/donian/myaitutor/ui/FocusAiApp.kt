package com.donian.myaitutor.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.donian.myaitutor.ai.GemmaAssistant
import com.donian.myaitutor.camera.CameraPreview
import com.donian.myaitutor.camera.CaptureImageAction
import com.donian.myaitutor.model.ModelDownloadManager
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val Subjects = listOf("�?��", "?�학", "?�어", "과학", "?�회", "??��", "?�리", "?�보", "기�?")

@Composable
fun FocusAiApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelManager = remember(context) { ModelDownloadManager(context) }
    val assistant = remember(context) { GemmaAssistant(context) }

    var showSettings by remember { mutableStateOf(false) }
    var selectedSubject by remember { mutableStateOf("?�학") }
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
    var backendLabel by remember { mutableStateOf("?�직 모델???�행?��? ?�았?�니??") }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (!granted) statusMessage = "카메??권한???�용?�야 문제�?촬영?????�습?�다."
    }

    LaunchedEffect(showSettings) {
        while (true) {
            modelSnapshot = modelManager.snapshot()
            delay(1_000)
        }
    }

    if (showSettings) {
        SettingsScreen(
            snapshot = modelSnapshot,
            backendLabel = backendLabel,
            onBack = { showSettings = false },
            onDownload = {
                runCatching { modelManager.startDownload() }
                    .onSuccess { statusMessage = "Wi-Fi�?AI 모델 ?�운로드�??�작?�습?�다." }
                    .onFailure { statusMessage = it.localizedMessage ?: "?�운로드�??�작?��? 못했?�니??" }
                modelSnapshot = modelManager.snapshot()
            },
            onDeleteModel = {
                scope.launch {
                    assistant.release()
                    modelManager.deleteModel()
                    modelSnapshot = modelManager.snapshot()
                    backendLabel = "모델????��?�었?�니??"
                }
            }
        )
    } else {
        HomeScreen(
            selectedSubject = selectedSubject,
            onSubjectSelected = { selectedSubject = it },
            cameraGranted = cameraGranted,
            onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
            onCaptureActionReady = { captureAction = it },
            onCameraError = { statusMessage = it },
            modelReady = modelSnapshot.status == ModelDownloadStatus.READY,
            onOpenSettings = { showSettings = true },
            answer = answer,
            followUp = followUp,
            onFollowUpChange = { followUp = it },
            isAnalyzing = isAnalyzing,
            statusMessage = statusMessage,
            onDismissStatus = { statusMessage = null },
            onSolve = {
                if (!cameraGranted) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else if (modelSnapshot.status != ModelDownloadStatus.READY) {
                    statusMessage = "?�정?�서 Gemma 4 E4B 모델??먼�? ?�운로드??주세??"
                    showSettings = true
                } else if (captureAction == null) {
                    statusMessage = "카메?��? 준비되지 ?�았?�니?? ?�시 ???�시 ?�도??주세??"
                } else {
                    val imageFile = File(context.cacheDir, "focus_capture_${System.currentTimeMillis()}.jpg")
                    isAnalyzing = true
                    answer = "문제�?촬영?�습?�다. Gemma 4 E4B가 ?��?지?�?문제 ?�용??분석?�고 ?�습?�다.\n\n"
                    statusMessage = null
                    captureAction?.invoke(imageFile) { result ->
                        result.onSuccess { captured ->
                            scope.launch {
                                try {
                                    assistant.analyzeImage(captured, selectedSubject) { newText ->
                                        answer = newText
                                    }
                                    backendLabel = "?�행 백엔?? ${assistant.backendLabel()}"
                                } catch (error: Throwable) {
                                    statusMessage = error.localizedMessage ?: "AI 분석 �??�류가 발생?�습?�다."
                                } finally {
                                    captured.delete()
                                    isAnalyzing = false
                                }
                            }
                        }.onFailure { error ->
                            isAnalyzing = false
                            statusMessage = error.localizedMessage ?: "?��?지�?촬영?��? 못했?�니??"
                        }
                    }
                }
            },
            onSendFollowUp = {
                val question = followUp.trim()
                if (question.isNotEmpty() && !isAnalyzing) {
                    if (modelSnapshot.status != ModelDownloadStatus.READY) {
                        statusMessage = "AI 모델??먼�? ?�치??주세??"
                    } else {
                        isAnalyzing = true
                        val previousAnswer = answer
                        answer = "$previousAnswer\n\n?�생: $question\n\nAI ?�생?? "
                        followUp = ""
                        scope.launch {
                            try {
                                assistant.askFollowUp(question) { newText ->
                                    answer = "$previousAnswer\n\n?�생: $question\n\nAI ?�생?? $newText"
                                }
                            } catch (error: Throwable) {
                                statusMessage = error.localizedMessage ?: "추�? 질문 처리 �??�류가 발생?�습?�다."
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
    cameraGranted: Boolean,
    onRequestCamera: () -> Unit,
    onCaptureActionReady: (CaptureImageAction?) -> Unit,
    onCameraError: (String) -> Unit,
    modelReady: Boolean,
    onOpenSettings: () -> Unit,
    answer: String,
    followUp: String,
    onFollowUpChange: (String) -> Unit,
    isAnalyzing: Boolean,
    statusMessage: String?,
    onDismissStatus: () -> Unit,
    onSolve: () -> Unit,
    onSendFollowUp: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            SubjectBar(selectedSubject = selectedSubject, onSubjectSelected = onSubjectSelected)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MyAITutor",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "문제�?보여주면, ?�해???�까지 ?�께 공�??�요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "?�정")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18263A)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    contentAlignment = Alignment.Center
                ) {
                    if (cameraGranted) {
                        CameraPreview(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(22.dp)),
                            onCaptureReady = onCaptureActionReady,
                            onCameraError = onCameraError
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .clip(CircleShape)
                                .background(Color(0x990D1728))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF49D49A)))
                                Spacer(Modifier.width(6.dp))
                                Text("CAMERA", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                            Text("문제�?카메?�에 비춰주세??, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("카메??권한???�용?�면 ???�역?�서 ?�시�?미리보기가 ?�작?�니??", color = Color(0xFFCFD8E5), style = MaterialTheme.typography.bodySmall)
                            Button(onClick = onRequestCamera) {
                                Text("카메???�용 ?�용")
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onSolve,
                enabled = !isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF245B9B),
                    disabledContainerColor = Color(0xFF9BB2CC)
                )
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(9.dp))
                    Text("AI ?�생?�이 분석?�고 ?�어??)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text("AI 문제 ?�??, fontWeight = FontWeight.Bold)
                }
            }

            if (!modelReady) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEAF0F8))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF315C9B))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AI 모델 ?�치가 ?�요?�니?? ?�측 ?�단 ?�정?�서 ??3.66GB 모델???�운로드?�세??",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF24466F),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (statusMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFF0E8))
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(statusMessage, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A3C16))
                    TextButton(onClick = onDismissStatus) { Text("?�기") }
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
                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("AI ?�생??, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("개념부??차근차근", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    if (answer.isBlank()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFB48A35), modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("?�직 ?�?��? ?�습?�다.", fontWeight = FontWeight.SemiBold)
                            Text(
                                "문제�?카메?�에 비추�?AI 문제 ?�?��? ?�러주세??",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                text = answer,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = 12.dp, end = 5.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = followUp,
                    onValueChange = onFollowUpChange,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box {
                            if (followUp.isEmpty()) {
                                Text("?�생?�에�?추�? 질문?�기", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            innerTextField()
                        }
                    }
                )
                IconButton(onClick = onSendFollowUp, enabled = followUp.isNotBlank() && !isAnalyzing) {
                    Icon(Icons.Default.Send, contentDescription = "질문 ?�송", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SubjectBar(selectedSubject: String, onSubjectSelected: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 5.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 5.dp)) {
            Text(
                "?�습 과목",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(Subjects) { subject ->
                    FilterChip(
                        selected = selectedSubject == subject,
                        onClick = { onSubjectSelected(subject) },
                        label = { Text(subject) }
                    )
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "?�로", modifier = Modifier.size(25.dp))
                }
                Text("?�정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                            Text("LiteRT-LM · ?�디바이??멀?�모??AI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("모델 ?�태", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (snapshot.status) {
                                ModelDownloadStatus.READY -> "?�치 ?�료"
                                ModelDownloadStatus.DOWNLOADING -> "?�운로드 �?
                                ModelDownloadStatus.QUEUED -> "?��?�?
                                ModelDownloadStatus.PAUSED -> "?�시 중�?"
                                ModelDownloadStatus.FAILED -> "?�류"
                                ModelDownloadStatus.NOT_INSTALLED -> "미설�?
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
                            Text("모델 ?�일??찾았?�니?? AI 분석???�용?????�습?�다.", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                            Text("모델 ??��")
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
                            Text(if (snapshot.status == ModelDownloadStatus.FAILED) "?�시 ?�운로드" else "AI 모델 ?�운로드")
                        }
                    }
                }
            }

            InfoCard(
                icon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "?�운로드 ?�내",
                body = "모델 ?�일?�???3.66GB?�니?? ?�이???�금??발생?��? ?�도�?Wi-Fi?�서�??�운로드?�도�??�정?�습?�다. ?�운로드가 ?�나�?AI 추론?�?기기 ?��??�서 ?�행?�니??"
            )
            InfoCard(
                icon = { Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "AI ?�생???�습 방식",
                body = "개념 ?�명 ???�심 ?�리 ??문제 ?�용 ???�계�??�????최종 ?��? �?검???�서�??�명?�니?? ?�????추�? 질문???�어�????�습?�다."
            )
            InfoCard(
                icon = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "개인?�보 �??�프?�인 처리",
                body = "문제 ?�진?�?분석???�해 ?�시 ?�일�??�?�한 ????��?�니?? AI 분석 ?�청�?추�? 질문?�??��? AI ?�버�??�송?��? ?�습?�다. 최초 모델 ?�운로드 �??�데?�트?�는 ?�터?�이 ?�요?�니??"
            )
            Text("MyAITutor 0.0.1 · Gemma 4 E4B-IT LiteRT-LM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("?�행 ?�경�?기기 메모리에 ?�라 모델 로딩 �?추론???�패?????�습?�다.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("?�재 백엔?? $backendLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("AI 모델????��?�까??") },
            text = { Text("??3.66GB??모델 ?�일??기기?�서 ??��?�니?? ?�시 ?�용?�려�??�다?�로?�해???�니??") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteModel()
                }) { Text("??��") }
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
