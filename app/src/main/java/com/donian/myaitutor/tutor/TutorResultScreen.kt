package com.donian.myaitutor.tutor

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.donian.myaitutor.R
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.camera.ExifImageTransform
import com.donian.myaitutor.ui.components.TeacherMessageBubble
import com.donian.myaitutor.ui.components.TypingIndicator
import com.donian.myaitutor.ui.components.UserMessageBubble
import com.donian.myaitutor.ui.home.ChatMessage
import com.donian.myaitutor.ui.home.ChatRole
import com.donian.myaitutor.ui.home.FollowUpChips
import com.donian.myaitutor.ui.text.AssistantAnswer
import com.donian.myaitutor.ui.text.parseAnswerWithSuggestions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 실시간 AI 선생님 화면 - 개선된 디자인.
 * AI 답변과 학생 질문을 개별 채팅 버블로 분리하여 표시.
 * 학년 칩은 HomeScreen 상단으로 이동됨.
 */
@Composable
internal fun TutorResultScreen(
    answer: String,
    tutorState: TutorUiState,
    selectedGrade: TutorGrade,
    onGradeSelected: (TutorGrade) -> Unit,
    ocrText: String,
    preprocessedImage: File?,
    capturedImageAvailable: Boolean,
    modelReady: Boolean,
    isAnalyzing: Boolean,
    showFollowUpChoices: Boolean,
    statusMessage: String?,
    canRetryAnalysis: Boolean,
    onOpenSettings: () -> Unit,
    onRetryAnalysis: () -> Unit,
    onCancelGeneration: () -> Unit,
    onRecoverTutor: () -> Unit,
    onSendChoice: (String) -> Unit,
    inputFocusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    var progressExpanded by remember { mutableStateOf(false) }
    val answerScrollState = rememberScrollState()
    var autoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(tutorState.isGenerating) {
        if (tutorState.isGenerating) progressExpanded = false
    }

    LaunchedEffect(answerScrollState) {
        snapshotFlow { answerScrollState.value }.collect { value ->
            autoScroll = value >= answerScrollState.maxValue - 24
        }
    }
    LaunchedEffect(answer, tutorState.currentStatus, messages.size) {
        if (autoScroll) {
            withFrameNanos { }
            if (!answerScrollState.isScrollInProgress) {
                answerScrollState.scrollTo(answerScrollState.maxValue)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        TutorStatusCard(
            tutorState = tutorState,
            canRetryAnalysis = canRetryAnalysis,
            progressExpanded = progressExpanded,
            onToggleProgress = { progressExpanded = !progressExpanded },
            onCancelGeneration = onCancelGeneration,
            onRetryAnalysis = onRetryAnalysis,
            onRecoverTutor = onRecoverTutor
        )
        if (tutorState.progressStage != TutorProgressStage.IDLE) {
            Spacer(Modifier.height(4.dp))
        }

        val parsedAnswer = remember(answer) { parseAnswerWithSuggestions(answer) }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(answerScrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (ocrText.isNotBlank() || preprocessedImage != null) {
                OcrTextDisclosure(text = ocrText, imageFile = preprocessedImage)
            }

            // 메시지 목록 표시 (AI 답변 + 학생 질문 분리)
            if (messages.isNotEmpty()) {
                messages.forEach { message ->
                    when (message.role) {
                        ChatRole.TEACHER -> {
                            TeacherMessageBubble {
                                SelectionContainer {
                                    AssistantAnswer(
                                        text = message.text,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        ChatRole.STUDENT -> {
                            UserMessageBubble {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            } else if (answer.isNotBlank()) {
                // 스트리밍 중인 답변 (아직 messages로 이동 전)
                TeacherMessageBubble {
                    SelectionContainer {
                        AssistantAnswer(
                            text = answer,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (isAnalyzing) {
                TypingIndicator()
            } else {
                EmptyAnswerPlaceholder(
                    capturedImageAvailable = capturedImageAvailable,
                    modelReady = modelReady,
                    onOpenSettings = onOpenSettings
                )
            }

            // 스트리밍 중일 때도 기존 answer 표시
            if (messages.isEmpty() && answer.isNotBlank() && isAnalyzing) {
                TeacherMessageBubble {
                    SelectionContainer {
                        AssistantAnswer(
                            text = answer,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            FollowUpChips(
                suggestions = parsedAnswer.suggestions,
                visible = showFollowUpChoices &&
                    capturedImageAvailable &&
                    modelReady &&
                    !isAnalyzing &&
                    statusMessage == null &&
                    !tutorState.requiresRecovery,
                isAnalyzing = isAnalyzing,
                inputFocusRequester = inputFocusRequester,
                keyboardController = keyboardController,
                onSendChoice = onSendChoice
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TutorStatusCard(
    tutorState: TutorUiState,
    canRetryAnalysis: Boolean,
    progressExpanded: Boolean,
    onToggleProgress: () -> Unit,
    onCancelGeneration: () -> Unit,
    onRetryAnalysis: () -> Unit,
    onRecoverTutor: () -> Unit
) {
    when {
        tutorState.requiresRecovery -> {
            StatusCard(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                borderColor = MaterialTheme.colorScheme.error
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tutorState.errorMessage ?: tutorState.currentStatus,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                TextButton(onClick = onRecoverTutor) {
                    Text(stringResource(R.string.tutor_btn_recover))
                }
            }
        }

        tutorState.errorMessage != null -> {
            StatusCard(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                borderColor = MaterialTheme.colorScheme.error
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tutorState.errorMessage,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (canRetryAnalysis) {
                    TextButton(onClick = onRetryAnalysis) {
                        Text(stringResource(R.string.btn_retry))
                    }
                }
            }
        }

        tutorState.isGenerating -> {
            // 로딩 상태: 원형 로딩 인디케이터는 HomeScreen 헤더 카드로 이동됨
            Spacer(Modifier.height(0.dp))
        }

        tutorState.isCancelled -> {
            StatusCard(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                borderColor = MaterialTheme.colorScheme.outlineVariant
            ) {
                Icon(
                    Icons.Default.StopCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tutorState.currentStatus.ifBlank {
                        stringResource(R.string.tutor_cancelled_status)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        tutorState.isCompleted -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleProgress() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tutorState.currentStatus.ifBlank {
                                stringResource(R.string.tutor_completed_status)
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.graphicsLayer(
                                rotationZ = if (progressExpanded) 180f else 0f
                            )
                        )
                    }
                    if (progressExpanded) {
                        ProgressDetails(tutorState.progressMessages)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    containerColor: Color,
    borderColor: Color,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun ProgressDetails(messages: List<String>) {
    if (messages.isEmpty()) return
    Column(modifier = Modifier.padding(top = 8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tutor_progress_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        messages.forEach { message ->
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyAnswerPlaceholder(
    capturedImageAvailable: Boolean,
    modelReady: Boolean,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
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
            text = stringResource(
                if (capturedImageAvailable) {
                    R.string.home_empty_answer_placeholder
                } else {
                    R.string.home_empty_capture_prompt
                }
            ),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(
                if (modelReady) {
                    R.string.home_empty_hint_after_capture
                } else {
                    R.string.home_empty_hint_need_model
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (!modelReady) {
            TextButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = stringResource(R.string.content_desc_download_model)
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.home_btn_model_settings))
            }
        }
    }
}

@Composable
private fun OcrTextDisclosure(text: String, imageFile: File?) {
    var expanded by remember(text, imageFile) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(if (expanded) R.string.home_ocr_hide else R.string.home_ocr_show),
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer(rotationZ = if (expanded) 180f else 0f)
                )
            }
            Text(
                stringResource(R.string.home_ocr_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            if (expanded) {
                if (imageFile != null) {
                    OcrInputImagePreview(imageFile)
                }
                if (text.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrInputImagePreview(imageFile: File) {
    val preview by produceState<ImageBitmap?>(initialValue = null, imageFile) {
        value = withContext(Dispatchers.IO) { decodeOcrPreview(imageFile) }
    }
    val bitmap = preview ?: return
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(
            stringResource(R.string.ocr_preprocessed_image_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(Modifier.height(6.dp))
        Image(
            bitmap = bitmap,
            contentDescription = stringResource(R.string.ocr_preprocessed_image_title),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit
        )
    }
}

private const val OCR_PREVIEW_MAX_SIDE_PX = 1280

private fun decodeOcrPreview(file: File): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= OCR_PREVIEW_MAX_SIDE_PX) {
        sample *= 2
    }
    val bitmap = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return null

    val oriented = try {
        val orientation = ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        ExifImageTransform.fromOrientation(orientation).apply(bitmap)
    } catch (_: Exception) {
        bitmap
    }
    if (oriented !== bitmap) bitmap.recycle()
    return oriented.asImageBitmap()
}