package com.donian.myaitutor.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.ui.home.Subjects
import com.donian.myaitutor.ui.text.AssistantAnswer
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
    inputText: String,
    onInputChange: (String) -> Unit,
    isAnalyzing: Boolean,
    statusMessage: String?,
    onDismissStatus: () -> Unit,
    onRetryAnalysis: () -> Unit,
    canRetryAnalysis: Boolean,
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
                Box {
                    OutlinedButton(
                        onClick = { subjectMenuExpanded = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
                    ) {
                        Text(selectedSubject, maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.content_desc_select_subject))
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
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.content_desc_open_settings))
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
                            if (isAnalyzing) stringResource(R.string.home_title_generating) else stringResource(R.string.home_title_generator),
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
                            FollowUpChips(
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