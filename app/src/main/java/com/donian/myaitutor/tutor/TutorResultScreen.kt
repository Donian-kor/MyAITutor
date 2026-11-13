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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.donian.myaitutor.ui.home.FollowUpChips
import com.donian.myaitutor.ui.text.AssistantAnswer
import com.donian.myaitutor.ui.text.parseAnswerWithSuggestions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 실시간 AI 선생님 화면. 학년 칩, 진행 상태 카드, 접기·펼치기 진행 내역,
 * 기존 AssistantAnswer(LaTeX/Markdown) 기반 실시간 해설을 표시합니다.
 *
 * 상태를 소유하지 않고 HomeViewModel에서 내려온 값만 그립니다.
 */
@Composable
internal fun TutorResultScreen(
    answer: String,
    tutorState: TutorUiState,
    selectedGrade: TutorGrade,
    onGradeSelected: (TutorGrade) -> Unit,
    ocrText: String,
    /** 크롭/전처리가 끝난 OCR 입력 이미지. 미리보기 표시용이며 null이면 생략한다. */
    preprocessedImage: File? = null,
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
    modifier: Modifier = Modifier
) {
    var progressExpanded by remember { mutableStateOf(false) }
    val answerScrollState = rememberScrollState()
    var autoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(tutorState.isGenerating) {
        if (tutorState.isGenerating) progressExpanded = false
    }

    // 사용자가 하단을 보고 있을 때만 자동 스크롤을 유지합니다.
    LaunchedEffect(answerScrollState) {
        snapshotFlow { answerScrollState.value }.collect { value ->
            autoScroll = value >= answerScrollState.maxValue - 24
        }
    }
    LaunchedEffect(answer, tutorState.currentStatus) {
        if (autoScroll) {
            withFrameNanos { }
            if (!answerScrollState.isScrollInProgress) {
                answerScrollState.scrollTo(answerScrollState.maxValue)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        GradeChips(
            selectedGrade = selectedGrade,
            onGradeSelected = onGradeSelected
        )
        Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(8.dp))
        }

        val parsedAnswer = remember(answer) { parseAnswerWithSuggestions(answer) }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(answerScrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (ocrText.isNotBlank() || preprocessedImage != null) {
                OcrTextDisclosure(text = ocrText, imageFile = preprocessedImage)
            }
            when {
                answer.isNotBlank() -> {
                    TeacherMessageBubble {
                        SelectionContainer {
                            AssistantAnswer(
                                text = answer,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                isAnalyzing -> {
                    TypingIndicator()
                }
                else -> {
                    EmptyAnswerPlaceholder(
                        capturedImageAvailable = capturedImageAvailable,
                        modelReady = modelReady,
                        onOpenSettings = onOpenSettings
                    )
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
        }
    }
}

@Composable
private fun GradeChips(
    selectedGrade: TutorGrade,
    onGradeSelected: (TutorGrade) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(TutorGrade.entries) { grade ->
            val selected = grade == selectedGrade
            FilterChip(
                selected = selected,
                onClick = { onGradeSelected(grade) },
                label = {
                    Text(
                        grade.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null
            )
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
            StatusCard(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                borderColor = MaterialTheme.colorScheme.outlineVariant
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tutorState.currentStatus.ifBlank {
                        "선생님이 문제를 꼼꼼히 살펴보고 있어요."
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!tutorState.isCancelling) {
                    TextButton(onClick = onCancelGeneration) {
                        Text(stringResource(R.string.tutor_btn_stop))
                    }
                }
            }
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

/** OCR에 전달되기 직전의 크롭·전처리 이미지를 접기 카드 안에서 보여줍니다. */
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

/** 미리보기 최대 장변(px). 원본 해상도 그대로 올리지 않아 OOM을 막습니다. */
private const val OCR_PREVIEW_MAX_SIDE_PX = 1280

/** 반드시 IO 스레드에서 호출합니다. */
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
