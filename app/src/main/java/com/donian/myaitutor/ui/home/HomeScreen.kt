package com.donian.myaitutor.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.ui.home.FollowUpChoices
import com.donian.myaitutor.ui.home.Subjects
import com.donian.myaitutor.ui.text.AssistantAnswer
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
    inputText: String,
    onInputChange: (String) -> Unit,
    isAnalyzing: Boolean,
    statusMessage: String?,
    onDismissStatus: () -> Unit,
    onSend: () -> Unit,
    onSendChoice: (String) -> Unit,
    showFollowUpChoices: Boolean,
    onCancelGeneration: () -> Unit
) {
    var subjectMenuExpanded by remember { mutableStateOf(false) }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenCamera,
                            enabled = !isAnalyzing,
                            modifier = if (compact) Modifier.size(48.dp) else Modifier.height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = if (compact) PaddingValues(0.dp) else PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            )
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = if (compact) "문제 촬영" else null,
                                modifier = Modifier.size(20.dp)
                            )
                            if (!compact) {
                                Spacer(Modifier.width(6.dp))
                                Text("촬영")
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 88.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp, max = 88.dp)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = inputText,
                                    onValueChange = onInputChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 24.dp, max = 64.dp)
                                        .focusRequester(inputFocusRequester),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    maxLines = 3,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(
                                        onSend = { if (inputText.isNotBlank() && !isAnalyzing) onSend() }
                                    ),
                                    decorationBox = { innerTextField ->
                                        Box {
                                            if (inputText.isEmpty()) {
                                                Text(
                                                    if (answer.isBlank()) "질문 입력" else "추가 질문 입력",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        }

                        if (isAnalyzing) {
                            FilledTonalButton(
                                onClick = onCancelGeneration,
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "답변 중단")
                            }
                        } else {
                            FilledIconButton(
                                onClick = onSend,
                                enabled = inputText.isNotBlank(),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "질문 보내기")
                            }
                        }
                    }
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
                        Icon(Icons.Default.SmartToy, contentDescription = "AI 도우미", tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isAnalyzing) "답변 생성 중" else "AI 생성기",
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
                                    "문제를 촬영하면 짧은 답변 후 원하는 설명을 고를 수 있습니다."
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
                        val answerScrollState = rememberScrollState()
                        LaunchedEffect(answer) {
                            answerScrollState.animateScrollTo(answerScrollState.maxValue)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(answerScrollState),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SelectionContainer {
                                AssistantAnswer(
                                    text = answer,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (showFollowUpChoices && capturedImage != null && modelReady && !isAnalyzing && statusMessage == null) {
                                Text(
                                    "다음에 알고 싶은 내용을 선택하세요",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    FollowUpChoices.forEach { choice ->
                                        AssistChip(
                                            onClick = {
                                                val prompt = choice.prompt
                                                if (prompt == null) {
                                                    inputFocusRequester.requestFocus()
                                                    keyboardController?.show()
                                                } else {
                                                    onSendChoice(prompt)
                                                }
                                            },
                                            label = { Text(choice.label) },
                                            enabled = !isAnalyzing
                                        )
                                    }
                                }
                            }
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
                }
            }
        }
    }
}