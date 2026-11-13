package com.donian.myaitutor.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.R
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.tutor.TutorResultScreen
import com.donian.myaitutor.tutor.TutorUiState
import com.donian.myaitutor.ui.components.TeacherAvatar
import java.io.File

/**
 * 메인(홈) 화면 - 개선된 디자인.
 *
 * 변경 사항:
 * - 과목 선택: 드롭다운 박스로 변경
 * - 학년 칩: 상단 헤더 카드와 채팅 카드 사이로 이동
 * - AI 답변/학생 질문 분리 표시
 */
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
    preprocessedImage: File?,
    inputText: String,
    onInputChange: (String) -> Unit,
    isAnalyzing: Boolean,
    analysisStage: ImageAnalysisStage?,
    tokenCount: Int,
    maxContextTokens: Int,
    statusMessage: String?,
    onDismissStatus: () -> Unit,
    onRetryAnalysis: () -> Unit,
    canRetryAnalysis: Boolean,
    onSend: () -> Unit,
    onSendChoice: (String) -> Unit,
    showFollowUpChoices: Boolean,
    onCancelGeneration: () -> Unit,
    selectedGrade: TutorGrade,
    onGradeSelected: (TutorGrade) -> Unit,
    tutorState: TutorUiState,
    onRecoverTutor: () -> Unit,
    messages: List<ChatMessage>
) {
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var headerVisible by remember { mutableStateOf(false) }
    var subjectDropdownExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { headerVisible = true }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Surface(
                color = colors.surface,
                tonalElevation = 4.dp,
                shadowElevation = 12.dp
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
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
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 상단 헤더 카드
            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
                    initialOffsetY = { -it / 3 },
                    animationSpec = tween(500)
                )
            ) {
                Surface(
                    shape = shapes.large,
                    color = colors.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 과목 선택 드롭다운
                        Box {
                            Surface(
                                shape = shapes.medium,
                                color = colors.primaryContainer,
                                modifier = Modifier
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        selectedSubject,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onPrimaryContainer
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "과목 선택",
                                        tint = colors.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            // 클릭 영역
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { subjectDropdownExpanded = true }
                            )
                            DropdownMenu(
                                expanded = subjectDropdownExpanded,
                                onDismissRequest = { subjectDropdownExpanded = false }
                            ) {
                                Subjects.forEach { subject ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                subject,
                                                fontWeight = if (subject == selectedSubject) FontWeight.Bold else FontWeight.Normal,
                                                color = if (subject == selectedSubject) colors.primary else colors.onSurface
                                            )
                                        },
                                        onClick = {
                                            onSubjectSelected(subject)
                                            subjectDropdownExpanded = false
                                        },
                                        leadingIcon = if (subject == selectedSubject) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // 문제 풀이 제목
                        Column(modifier = Modifier.weight(1f)) {
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
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface
                            )
                            if (tokenCount > 0) {
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    shape = shapes.small,
                                    color = colors.surfaceVariant
                                ) {
                                    Text(
                                        text = "$tokenCount / $maxContextTokens 토큰",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // 설정 버튼 또는 로딩 인디케이터
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = colors.primary
                            )
                        } else {
                            Surface(
                                shape = shapes.medium,
                                color = colors.surfaceVariant,
                                modifier = Modifier.size(44.dp)
                            ) {
                                IconButton(onClick = onOpenSettings) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.content_desc_open_settings),
                                        tint = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 학년 선택 칩 - 헤더 카드와 채팅 카드 사이
            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 100)),
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
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = shapes.large,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary,
                                selectedLeadingIconColor = colors.onPrimary,
                                containerColor = colors.surfaceVariant,
                                labelColor = colors.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            // 대화 영역 카드
            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 200)),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    shape = shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = colors.outlineVariant
                        )

                        // 튜터 결과 영역
                        TutorResultScreen(
                            answer = answer,
                            tutorState = tutorState,
                            selectedGrade = selectedGrade,
                            onGradeSelected = onGradeSelected,
                            ocrText = ocrText,
                            preprocessedImage = preprocessedImage,
                            capturedImageAvailable = capturedImage != null,
                            modelReady = modelReady,
                            isAnalyzing = isAnalyzing,
                            showFollowUpChoices = showFollowUpChoices,
                            statusMessage = statusMessage,
                            canRetryAnalysis = canRetryAnalysis,
                            onOpenSettings = onOpenSettings,
                            onRetryAnalysis = onRetryAnalysis,
                            onCancelGeneration = onCancelGeneration,
                            onRecoverTutor = onRecoverTutor,
                            onSendChoice = onSendChoice,
                            inputFocusRequester = inputFocusRequester,
                            keyboardController = keyboardController,
                            messages = messages,
                            modifier = Modifier.weight(1f)
                        )

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
}