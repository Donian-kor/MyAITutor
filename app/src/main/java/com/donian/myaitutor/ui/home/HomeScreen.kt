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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.R
import com.donian.myaitutor.ai.TutorGrade
import com.donian.myaitutor.tutor.TutorResultScreen
import com.donian.myaitutor.tutor.TutorUiState
import com.donian.myaitutor.ui.components.TeacherAvatar
import java.io.File

/**
 * 메인(홈) 화면 - 반응형 개선 디자인.
 *
 * 구조:
 * - 헤더 카드: 아바타 + 문제풀이 제목 + 토큰 수 + 설정/로딩
 * - 과목/학년 라인: 학년 칩(좌측) + 과목 드롭다운(우측)
 * - 대화 카드: 채팅 영역
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
    chatMode: Boolean,
    onChatModeChange: (Boolean) -> Unit,
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
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    val compact = maxWidth < 360.dp
                    ComposerBar(
                        compact = compact,
                        inputText = inputText,
                        answer = answer,
                        isAnalyzing = isAnalyzing,
                        // 자유 대화 모드에서는 문제를 촬영하지 않습니다.
                        captureEnabled = !chatMode,
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
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // === 상단 헤더 카드 ===
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
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 앱 아이콘 - 배경 스페이서 없이 헤더 높이에 맞게 확대
                        TeacherAvatar(size = 48)

                        // 문제 풀이 제목 + 토큰 수
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                when {
                                    chatMode -> stringResource(R.string.home_chat_mode_label)
                                    analysisStage == ImageAnalysisStage.OCR ->
                                        stringResource(R.string.home_stage_ocr)
                                    analysisStage == ImageAnalysisStage.GEMMA ->
                                        stringResource(R.string.home_stage_gemma)
                                    isAnalyzing -> stringResource(R.string.home_stage_gemma)
                                    else -> stringResource(R.string.home_title_generator)
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (tokenCount > 0) {
                                Spacer(Modifier.height(3.dp))
                                Surface(
                                    shape = shapes.small,
                                    color = colors.surfaceVariant
                                ) {
                                    Text(
                                        text = "$tokenCount / $maxContextTokens 토큰",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // 앱 로딩 원형 아이콘 또는 설정 버튼
                        if (isAnalyzing || analysisStage != null) {
                            // 분석/로딩 중: 앱 로딩 원형 인디케이터
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 4.dp,
                                color = colors.primary
                            )
                        } else {
                            Surface(
                                shape = shapes.medium,
                                color = colors.surfaceVariant,
                                modifier = Modifier.size(42.dp)
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

            // === 과목 드롭다운 + 학년 칩 라인 ===
            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 100)),
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isCompact = maxWidth < 400.dp

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 자유 대화 모드 스위치
                        FilterChip(
                            selected = chatMode,
                            onClick = { onChatModeChange(!chatMode) },
                            label = {
                                Text(
                                    stringResource(R.string.home_chat_mode_label),
                                    fontWeight = if (chatMode) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (chatMode) {
                                        Icons.Default.Forum
                                    } else {
                                        Icons.Default.School
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = shapes.large,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.tertiary,
                                selectedLabelColor = colors.onTertiary,
                                selectedLeadingIconColor = colors.onTertiary,
                                containerColor = colors.surfaceVariant,
                                labelColor = colors.onSurfaceVariant
                            )
                        )

                        // 학년 칩 (좌측, 스크롤 가능)
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1
                                        )
                                    },
                                    leadingIcon = if (selected && !isCompact) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
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

                        // 과목 선택 드롭다운 (우측) - 자유 대화 모드에서는 과목이 무의미해 숨깁니다.
                        if (!chatMode) {
                        Box {
                            Surface(
                                shape = shapes.medium,
                                color = colors.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clickable { subjectDropdownExpanded = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        selectedSubject,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "과목 선택",
                                        tint = colors.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
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
                        }
                    }
                }
            }

            // === 대화 영역 카드 ===
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
                            .padding(16.dp)
                    ) {
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
                            selectedSubject = selectedSubject,
                            chatMode = chatMode,
                            onOpenCamera = onOpenCamera,
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