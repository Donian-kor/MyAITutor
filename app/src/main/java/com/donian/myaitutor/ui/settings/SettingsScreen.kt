package com.donian.myaitutor.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.BuildConfig
import com.donian.myaitutor.R
import com.donian.myaitutor.model.AppModel
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus
import com.donian.myaitutor.ui.components.TeacherAvatar
import com.donian.myaitutor.ui.components.InfoCard

/**
 * 설정 화면 - 대폭 개선된 디자인.
 *
 * 개선 사항:
 * - 테마 기반의 일관된 스타일링 (Shape, Typography)
 * - 과감한 레이아웃 변경: 플로팅 헤더 + 그룹화된 카드
 * - 등장 애니메이션 추가
 * - 개선된 명암비와 접근성
 */
@Composable
internal fun SettingsScreen(
    snapshot: ModelDownloadSnapshot,
    backendLabel: String,
    thinkingMode: Boolean,
    onThinkingModeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onDeleteModel: () -> Unit,
    onClearCache: () -> Unit,
    selectedModel: AppModel = AppModel.GEMMA,
    qwenSnapshot: ModelDownloadSnapshot = snapshot,
    isModelTransitioning: Boolean = false,
    modelSelectionBlockedReason: String? = null,
    onModelSelected: (AppModel) -> Unit = {},
    /** 지정한 모델의 다운로드를 시작한다. Gemma/Qwen 각각 독립적으로 동작한다. */
    onDownloadModel: (AppModel) -> Unit = { onDownload() },
    /** 지정한 모델의 파일을 삭제한다. 활성 모델 삭제 시 엔진이 먼저 해제된다. */
    onDeleteModelFile: (AppModel) -> Unit = { onDeleteModel() }
) {
    var showLicensesDialog by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 플로팅 헤더 - 과감한 변경
            AnimatedVisibility(
                visible = visible,
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
                        // 백 버튼 - 테마 shape 사용
                        Surface(
                            shape = shapes.medium,
                            color = colors.surfaceVariant,
                            modifier = Modifier.size(44.dp)
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.content_desc_back),
                                    tint = colors.onSurfaceVariant
                                )
                            }
                        }

                        // AI 선생님 아바타
                        TeacherAvatar(size = 48)

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "AI 선생님 설정",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2) 통합 AI 모델 선택 및 다운로드 카드 (Gemma / Qwen 선택, 상태, 다운로드/삭제)
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 100))
            ) {
                ModelSelectionCard(
                    selectedModel = selectedModel,
                    gemmaSnapshot = snapshot,
                    qwenSnapshot = qwenSnapshot,
                    isTransitioning = isModelTransitioning,
                    blockedReason = modelSelectionBlockedReason,
                    onModelSelected = onModelSelected,
                    onDownloadModel = onDownloadModel,
                    onDeleteModelFile = onDeleteModelFile
                )
            }

            // 3) 추론(Thinking) 모드 설정 카드
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 150))
            ) {
                ThinkingModeCard(
                    thinkingMode = thinkingMode,
                    onThinkingModeChange = onThinkingModeChange
                )
            }

            // 4) 정보 카드들 (기존과 동일)
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 300))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    var modelInfoExpanded by remember { mutableStateOf(false) }
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { modelInfoExpanded = !modelInfoExpanded }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.settings_ocr_title),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        if (modelInfoExpanded) "터치하여 접기" else "터치하여 펼치기",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    if (modelInfoExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = modelInfoExpanded) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    HorizontalDivider(color = colors.outlineVariant)

                                    // 1. Gemma 4 E4B-IT
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            "1. Gemma 4 E4B-IT (자유대화+학습 모델)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "• 파일명: gemma-4-E4B-it.litertlm (약 3.66GB)\n• 아키텍처: LiteRT-LM 멀티모달 모델\n• 특징: 과목 문제 풀이 및 자유 대화 모드 지원, 온디바이스 추론",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }

                                    // 2. Qwen3-4B-Instruct-2507
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            "2. Qwen3-4B-Instruct-2507 (학습전용 모델)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "• 파일명: qwen3_4b_instruct_2507_mixed_int4.litertlm (약 2.66GB)\n• 아키텍처: LiteRT-LM 텍스트 모델\n• 특징: 과목 학습 모드 전용 (자유 대화 미지원), typed parts Jinja 템플릿 적용",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }

                                    // 3. OCR 모델
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            "3. OCR 문자 인식 모델",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "• 인식 모델: korean_PP-OCRv5_mobile_rec\n• 검출 모델: models/det/inference.onnx\n• Android SDK: PaddlePaddle PaddleOCR Android\n• 문서: https://www.paddleocr.ai",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    InfoCard(
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp)) },
                        title = stringResource(R.string.info_learning_title),
                        body = stringResource(R.string.info_learning_body)
                    )
                    InfoCard(
                        icon = { Icon(Icons.Default.History, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp)) },
                        title = stringResource(R.string.info_privacy_title),
                        body = stringResource(R.string.info_privacy_body)
                    )
                }
            }

            // 버전 정보 - 개선된 디자인
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 400))
            ) {
                Surface(
                    shape = shapes.large,
                    color = colors.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            stringResource(
                                R.string.settings_app_version,
                                BuildConfig.VERSION_NAME
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.settings_runtime_env),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.settings_current_backend, backendLabel),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            stringResource(
                                R.string.settings_litertlm_version,
                                BuildConfig.LITERTLM_VERSION
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
            // 5) 임시 파일 캐시 정리 / 오픈소스 라이선스 (최하단)
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 500))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onClearCache,
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large
                    ) {
                        Text(stringResource(R.string.settings_btn_clear_cache))
                    }

                    OutlinedButton(
                        onClick = { showLicensesDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large
                    ) {
                        Text(stringResource(R.string.settings_btn_licenses))
                    }
                }
            }
        }
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text(stringResource(R.string.dialog_licenses_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_licenses_content,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) { Text(stringResource(R.string.btn_dismiss)) }
            }
        )
    }
}