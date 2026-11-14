package com.donian.myaitutor.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.R
import com.donian.myaitutor.model.AppModel
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus

/**
 * 통합 AI 모델 선택 및 다운로드/검증 카드.
 *
 * Gemma 4 E4B와 Qwen3-4B-Instruct-2507 모델의 다운로드 상태, 진행률, 용량을 표시하고
 * 설치가 완료된 모델은 라디오 클릭 시 바로 활성 모델로 선택·전환된다.
 *
 * @param blockedReason null이 아니면 선택을 보류하고 안내 문구를 표시한다 (계획서 0.6).
 */
@Composable
fun ModelSelectionCard(
    selectedModel: AppModel,
    gemmaSnapshot: ModelDownloadSnapshot,
    qwenSnapshot: ModelDownloadSnapshot,
    isTransitioning: Boolean,
    blockedReason: String?,
    onModelSelected: (AppModel) -> Unit,
    onDownloadModel: (AppModel) -> Unit,
    onDeleteModelFile: (AppModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var modelToDelete by remember { mutableStateOf<AppModel?>(null) }

    Card(
        shape = shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 320.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.model_selection_title),
                        style = if (compact) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!compact) {
                        Text(
                            stringResource(R.string.model_download_wifi_notice),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IntegratedModelRow(
                    label = stringResource(R.string.model_gemma_name),
                    description = stringResource(R.string.model_gemma_desc),
                    sizeLabel = stringResource(R.string.model_gemma_size),
                    snapshot = gemmaSnapshot,
                    isSelected = selectedModel == AppModel.GEMMA,
                    isEnabled = !isTransitioning && blockedReason == null,
                    onSelect = { onModelSelected(AppModel.GEMMA) },
                    onDownload = { onDownloadModel(AppModel.GEMMA) },
                    onDelete = { modelToDelete = AppModel.GEMMA }
                )

                IntegratedModelRow(
                    label = stringResource(R.string.model_qwen_name),
                    description = stringResource(R.string.model_qwen_desc),
                    sizeLabel = stringResource(R.string.model_qwen_size),
                    snapshot = qwenSnapshot,
                    isSelected = selectedModel == AppModel.QWEN3,
                    isEnabled = !isTransitioning && blockedReason == null,
                    onSelect = { onModelSelected(AppModel.QWEN3) },
                    onDownload = { onDownloadModel(AppModel.QWEN3) },
                    onDelete = { modelToDelete = AppModel.QWEN3 }
                )
            }

            if (blockedReason != null) {
                Text(
                    blockedReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error
                )
            } else if (isTransitioning) {
                Text(
                    stringResource(R.string.model_transitioning_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }

            Text(
                stringResource(R.string.model_single_load_notice),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }

    if (modelToDelete != null) {
        val targetModel = modelToDelete!!
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_model_title)) },
            text = {
                Text(
                    if (targetModel == AppModel.QWEN3) {
                        stringResource(R.string.dialog_delete_model_message_qwen)
                    } else {
                        stringResource(R.string.dialog_delete_model_message)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    modelToDelete = null
                    onDeleteModelFile(targetModel)
                }) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

/** 하위 호환성을 위해 [ModelDownloadCard] 시그니처를 유지하는 오버로드. */
@Composable
fun ModelSelectionCard(
    selectedModel: AppModel,
    gemmaStatus: ModelDownloadStatus,
    qwenStatus: ModelDownloadStatus,
    isTransitioning: Boolean,
    blockedReason: String?,
    onModelSelected: (AppModel) -> Unit,
    modifier: Modifier = Modifier
) {
    ModelSelectionCard(
        selectedModel = selectedModel,
        gemmaSnapshot = gemmaSnapshotOf(gemmaStatus),
        qwenSnapshot = gemmaSnapshotOf(qwenStatus),
        isTransitioning = isTransitioning,
        blockedReason = blockedReason,
        onModelSelected = onModelSelected,
        onDownloadModel = {},
        onDeleteModelFile = {},
        modifier = modifier
    )
}

@Composable
private fun IntegratedModelRow(
    label: String,
    description: String,
    sizeLabel: String,
    snapshot: ModelDownloadSnapshot,
    isSelected: Boolean,
    isEnabled: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val isReady = snapshot.status == ModelDownloadStatus.READY
    val isBusy = snapshot.status == ModelDownloadStatus.DOWNLOADING ||
        snapshot.status == ModelDownloadStatus.QUEUED ||
        snapshot.status == ModelDownloadStatus.PAUSED ||
        snapshot.status == ModelDownloadStatus.VERIFYING

    Surface(
        shape = shapes.large,
        color = if (isSelected) colors.primaryContainer else colors.surfaceVariant,
        border = if (isSelected) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                enabled = isEnabled,
                role = Role.RadioButton,
                onClick = onSelect
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                    enabled = isEnabled
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isReady) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.content_desc_model_installed),
                                tint = colors.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (isBusy) {
                            statusLabel(snapshot.status)
                        } else {
                            "$sizeLabel · ${statusLabel(snapshot.status)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isReady) colors.tertiary else colors.onSurfaceVariant,
                        fontWeight = if (isReady) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            if (snapshot.message.isNotBlank()) {
                Text(
                    snapshot.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (snapshot.status == ModelDownloadStatus.FAILED) colors.error else colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp)
                )
            }

            if (isBusy) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { snapshot.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(shapes.small),
                        color = colors.primary,
                        trackColor = colors.outlineVariant
                    )
                    if (snapshot.totalBytes > 0L) {
                        Text(
                            "${snapshot.downloadedLabel} / ${snapshot.totalLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (isReady) {
                    if (isSelected) {
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.fillMaxWidth(),
                            shape = shapes.large,
                            border = BorderStroke(1.dp, colors.error.copy(alpha = 0.35f)),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = colors.errorContainer.copy(alpha = 0.45f),
                                contentColor = colors.error
                            )
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.settings_btn_model_delete),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_btn_model_delete))
                        }
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = !isBusy && isEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = stringResource(R.string.settings_btn_download),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (snapshot.status) {
                                ModelDownloadStatus.FAILED -> stringResource(R.string.settings_btn_download_retry)
                                ModelDownloadStatus.PAUSED, ModelDownloadStatus.QUEUED ->
                                    stringResource(R.string.settings_btn_download_resume)
                                else -> stringResource(R.string.settings_btn_download)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** [ModelDownloadStatus] 단독 전달용 Helper. */
private fun gemmaSnapshotOf(status: ModelDownloadStatus): ModelDownloadSnapshot =
    ModelDownloadSnapshot(
        status = status,
        downloadedBytes = 0L,
        totalBytes = 0L,
        message = ""
    )

/**
 * 추론(Thinking) 모드 토글 카드.
 *
 * 학년과 무관하게 사용자가 직접 켜고 끈다. 켜면 답을 내기 전에 단계를 따져 생각하므로
 * 정확도는 올라가지만 답변이 느려지고 토큰을 더 사용한다.
 *
 * Qwen은 Thinking을 항상 끄고 동작하므로 [thinkingSupported]가 false이면 스위치를 비활성화하고
 * 사유를 안내한다.
 */
@Composable
fun ThinkingModeCard(
    thinkingMode: Boolean,
    onThinkingModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    thinkingSupported: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes

    Surface(
        shape = shapes.extraLarge,
        color = colors.surface,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
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
                    if (thinkingSupported) {
                        stringResource(R.string.settings_thinking_desc)
                    } else {
                        stringResource(R.string.settings_thinking_qwen_note)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Switch(
                checked = if (thinkingSupported) thinkingMode else false,
                onCheckedChange = onThinkingModeChange,
                enabled = thinkingSupported
            )
        }
    }
}

@Composable
private fun statusLabel(status: ModelDownloadStatus): String = when (status) {
    ModelDownloadStatus.READY -> stringResource(R.string.model_status_ready)
    ModelDownloadStatus.DOWNLOADING -> stringResource(R.string.model_status_downloading)
    ModelDownloadStatus.QUEUED -> stringResource(R.string.model_status_queued)
    ModelDownloadStatus.PAUSED -> stringResource(R.string.model_status_paused)
    ModelDownloadStatus.VERIFYING -> stringResource(R.string.model_status_verifying)
    ModelDownloadStatus.FAILED -> stringResource(R.string.model_status_failed)
    ModelDownloadStatus.NOT_INSTALLED -> stringResource(R.string.model_status_not_installed)
}