package com.donian.myaitutor.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.ui.home.Subjects
import com.donian.myaitutor.ui.text.AssistantAnswer
import com.donian.myaitutor.ui.text.parseAnswerWithSuggestions
import com.donian.myaitutor.R
import java.io.File

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HomeScreen(
    selectedSubject: String,
    onSubjectSelected: (String) -> Unit,
    capturedImage: File?,
    modelReady: Boolean,
    onOpenSettings: () -> Unit,
    onOpenCamera: () -> Unit,
    answer: String,
    ocrText: String,
    inputText: String,
    onInputChange: (String) -> Unit,
    isAnalyzing: Boolean,
    analysisStage: ImageAnalysisStage?,
    statusMessage: String?,
    onDismissStatus: () -> Unit,
    onRetryAnalysis: () -> Unit,
    canRetryAnalysis: Boolean,
    onSend: () -> Unit,
    onSendChoice: (String) -> Unit,
    showFollowUpChoices: Boolean,
    onCancelGeneration: () -> Unit
) {
    var showOcrText by remember(ocrText) { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 3.dp
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    val compact = maxWidth < 360.dp
                    ComposerBar(
                        compact = compact,
                        inputText = inputText,
                        answer = answer,
                        isAnalyzing = isAnalyzing,
                        inputFocusRequester = inputFocusRequester,
                        onInputChange = onInputChange,
                        onOpenCamera = onOpenCamera,
                        onSend = onSend,
                        onCancelGeneration = onCancelGeneration
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                    text = stringResource(R.string.app_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.content_desc_open_settings))
                }
            }

            // 가로 스크롤 과목 선택 칩 바 (LazyRow를 사용해 부드러운 터치 스와이프 및 스크롤 지원)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(Subjects) { subject ->
                    val isSelected = subject == selectedSubject
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSubjectSelected(subject) },
                        label = {
                            Text(
                                subject,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
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
                        Icon(Icons.Default.SmartToy, contentDescription = stringResource(R.string.content_desc_ai_helper), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (analysisStage) {
                                ImageAnalysisStage.OCR -> stringResource(R.string.home_stage_ocr)
                                ImageAnalysisStage.GEMMA -> stringResource(R.string.home_stage_gemma)
                                null -> if (isAnalyzing) {
                                    stringResource(R.string.home_stage_gemma)
                                } else {
                                    stringResource(R.string.home_title_generator)
                                }
                            },
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
                            if (isAnalyzing) {
                                if (ocrText.isNotBlank()) {
                                    OcrTextDisclosure(
                                        text = ocrText,
                                        expanded = showOcrText,
                                        onToggle = { showOcrText = !showOcrText }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                                Text(
                                    when (analysisStage) {
                                        ImageAnalysisStage.OCR -> stringResource(R.string.home_stage_ocr_detail)
                                        ImageAnalysisStage.GEMMA -> stringResource(R.string.home_stage_gemma_detail)
                                        null -> stringResource(R.string.home_stage_gemma_detail)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFB48A35),
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (capturedImage == null) stringResource(R.string.home_empty_capture_prompt) else stringResource(R.string.home_empty_answer_placeholder),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (modelReady) {
                                        stringResource(R.string.home_empty_hint_after_capture)
                                    } else {
                                        stringResource(R.string.home_empty_hint_need_model)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                if (!modelReady) {
                                    TextButton(onClick = onOpenSettings) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = stringResource(R.string.content_desc_download_model))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.home_btn_model_settings))
                                    }
                                }
                            }
                        }
                    } else {
                        val answerScrollState = rememberScrollState()
                        val parsedAnswer = remember(answer) { parseAnswerWithSuggestions(answer) }
                        LaunchedEffect(answer) {
                            val wasAtBottom = answerScrollState.maxValue == 0 ||
                                answerScrollState.value >= answerScrollState.maxValue - 32
                            if (wasAtBottom && !answerScrollState.isScrollInProgress) {
                                withFrameNanos { }
                                if (!answerScrollState.isScrollInProgress) {
                                    answerScrollState.scrollTo(answerScrollState.maxValue)
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(answerScrollState),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (ocrText.isNotBlank()) {
                                OcrTextDisclosure(
                                    text = ocrText,
                                    expanded = showOcrText,
                                    onToggle = { showOcrText = !showOcrText }
                                )
                            }
                            SelectionContainer {
                                AssistantAnswer(
                                    text = answer,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            FollowUpChips(
                                suggestions = parsedAnswer.suggestions,
                                visible = showFollowUpChoices && capturedImage != null && modelReady && !isAnalyzing && statusMessage == null,
                                isAnalyzing = isAnalyzing,
                                inputFocusRequester = inputFocusRequester,
                                keyboardController = keyboardController,
                                onSendChoice = onSendChoice
                            )
                        }
                    }
                    StatusBanner(
                        message = statusMessage,
                        canRetry = canRetryAnalysis,
                        onRetry = onRetryAnalysis,
                        onDismiss = onDismissStatus
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrTextDisclosure(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            TextButton(
                onClick = onToggle,
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