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
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Wifi
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
    onModelSelected: (AppModel) -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    val isBusy = snapshot.status == ModelDownloadStatus.DOWNLOADING ||
        snapshot.status == ModelDownloadStatus.QUEUED ||
        snapshot.status == ModelDownloadStatus.PAUSED ||
        snapshot.status == ModelDownloadStatus.VERIFYING
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

            // AI 선생님 모델 카드 - 개선된 디자인
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 100))
            ) {
                Card(
                    shape = shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 모델 헤더
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = shapes.medium,
                                color = colors.primaryContainer,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(R.drawable.robot_tutor),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Gemma 4 E4B-IT",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    stringResource(R.string.settings_engine_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = colors.outlineVariant)

                        // 모델 상태
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.settings_model_status),
                                modifier = Modifier.weight(1f),
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                when (snapshot.status) {
                                    ModelDownloadStatus.READY -> stringResource(R.string.model_status_ready)
                                    ModelDownloadStatus.DOWNLOADING -> stringResource(R.string.model_status_downloading)
                                    ModelDownloadStatus.QUEUED -> stringResource(R.string.model_status_queued)
                                    ModelDownloadStatus.PAUSED -> stringResource(R.string.model_status_paused)
                                    ModelDownloadStatus.VERIFYING -> stringResource(R.string.model_status_verifying)
                                    ModelDownloadStatus.FAILED -> stringResource(R.string.model_status_failed)
                                    ModelDownloadStatus.NOT_INSTALLED -> stringResource(R.string.model_status_not_installed)
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (snapshot.status == ModelDownloadStatus.READY) colors.tertiary else colors.onSurface
                            )
                        }

                        // 상태 메시지
                        if (snapshot.message.isNotBlank()) {
                            Text(
                                snapshot.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }

                        // 진행 표시
                        if (isBusy || snapshot.status == ModelDownloadStatus.READY) {
                            LinearProgressIndicator(
                                progress = { if (snapshot.status == ModelDownloadStatus.READY) 1f else snapshot.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(shapes.small),
                                color = colors.primary,
                                trackColor = colors.outlineVariant
                            )
                            Text(
                                "${snapshot.downloadedLabel} / ${snapshot.totalLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }

                        // 액션 버튼
                        if (snapshot.status == ModelDownloadStatus.READY) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = stringResource(R.string.content_desc_model_installed),
                                    tint = colors.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.settings_model_found),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = shapes.large
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.settings_btn_model_delete), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.settings_btn_model_delete))
                            }
                        } else {
                            Button(
                                onClick = onDownload,
                                enabled = snapshot.status != ModelDownloadStatus.DOWNLOADING,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = shapes.large
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = stringResource(R.string.settings_btn_download), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when (snapshot.status) {
                                        ModelDownloadStatus.FAILED -> stringResource(R.string.settings_btn_download_retry)
                                        ModelDownloadStatus.PAUSED, ModelDownloadStatus.QUEUED -> stringResource(R.string.settings_btn_download_resume)
                                        else -> stringResource(R.string.settings_btn_download)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 모델 선택 카드 (Gemma / Qwen). 기존 Gemma 카드를 대체하지 않고 별도 추가한다.
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 120))
            ) {
                ModelSelectionCard(
                    selectedModel = selectedModel,
                    gemmaStatus = snapshot.status,
                    qwenStatus = qwenSnapshot.status,
                    isTransitioning = isModelTransitioning,
                    blockedReason = modelSelectionBlockedReason,
                    onModelSelected = onModelSelected
                )
            }

            // 추론(Thinking) 모드 설정
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 150))
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
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_thinking_title),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.settings_thinking_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Switch(checked = thinkingMode, onCheckedChange = onThinkingModeChange)
                    }
                }
            }

            // 설정 액션 버튼들 - 개선된 간격
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 200))
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

            // 정보 카드들 - 개선된 간격
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 300))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    InfoCard(
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp)) },
                        title = stringResource(R.string.settings_ocr_title),
                        body = stringResource(R.string.settings_ocr_details)
                    )
                    InfoCard(
                        icon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp)) },
                        title = stringResource(R.string.info_download_title),
                        body = stringResource(R.string.info_download_body)
                    )
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
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_model_title)) },
            text = { Text(stringResource(R.string.dialog_delete_model_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteModel()
                }) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
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