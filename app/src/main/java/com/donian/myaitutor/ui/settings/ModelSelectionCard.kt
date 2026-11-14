package com.donian.myaitutor.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.R
import com.donian.myaitutor.model.AppModel
import com.donian.myaitutor.model.ModelDownloadSnapshot
import com.donian.myaitutor.model.ModelDownloadStatus

/**
 * Gemma / Qwen 모델 다운로드 및 무결성 검증 카드.
 *
 * 두 모델의 파일을 각각 독립적으로 내려받고 검증한다. 다운로드와 검증은 디스크 작업이며
 * 메모리에 엔진을 올리지 않으므로, Qwen을 내려받는 동안에도 현재 모델(Gemma)을 계속
 * 사용할 수 있다 (계획서 0.3 5항).
 *
 * @param selectedModel 현재 선택된 모델. 이 모델의 섹션을 강조한다.
 * @param onDownload 시작할 모델. null이면 선택 모델을 내려받는다.
 * @param onDelete 삭제할 모델. null이면 선택 모델을 삭제한다.
 */
@Composable
fun ModelDownloadCard(
    gemmaSnapshot: ModelDownloadSnapshot,
    qwenSnapshot: ModelDownloadSnapshot,
    selectedModel: AppModel,
    isTransitioning: Boolean,
    onDownload: (AppModel) -> Unit,
    onDelete: (AppModel) -> Unit,
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
            Text(
                stringResource(R.string.model_download_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            ModelDownloadSection(
                label = stringResource(R.string.model_gemma_name),
                sizeLabel = stringResource(R.string.model_gemma_size),
                snapshot = gemmaSnapshot,
                highlighted = selectedModel == AppModel.GEMMA,
                onDownload = { onDownload(AppModel.GEMMA) },
                onDelete = { modelToDelete = AppModel.GEMMA }
            )

            ModelDownloadSection(
                label = stringResource(R.string.model_qwen_name),
                sizeLabel = stringResource(R.string.model_qwen_size),
                snapshot = qwenSnapshot,
                highlighted = selectedModel == AppModel.QWEN3,
                onDownload = { onDownload(AppModel.QWEN3) },
                onDelete = { modelToDelete = AppModel.QWEN3 }
            )

            if (isTransitioning) {
                Text(
                    stringResource(R.string.model_transitioning_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }

    if (modelToDelete != null) {
        val targetModel = modelToDelete!!
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_model_title)) },
            text = { Text(stringResource(R.string.dialog_delete_model_message)) },
            confirmButton = {
                TextButton(onClick = {
                    modelToDelete = null
                    onDelete(targetModel)
                }) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

@Composable
private fun ModelDownloadSection(
    label: String,
    sizeLabel: String,
    snapshot: ModelDownloadSnapshot,
    highlighted: Boolean,
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
        color = if (highlighted) colors.primaryContainer else colors.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$sizeLabel · ${statusLabel(snapshot.status)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
                if (isReady) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.content_desc_model_installed),
                        tint = colors.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (snapshot.message.isNotBlank()) {
                Text(
                    snapshot.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            if (isBusy || isReady) {
                LinearProgressIndicator(
                    progress = { if (isReady) 1f else snapshot.progress },
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

            if (isReady) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = shapes.large
                ) {
                    Text(stringResource(R.string.settings_btn_model_delete))
                }
            } else {
                Button(
                    onClick = onDownload,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
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

/**
 * AI 모델 선택 카드.
 *
 * Gemma 4 E4B / Qwen3-4B-Instruct-2507 중 하나를 선택한다.
 * 선택 가능한 조건은 [blockedReason]이 null인 경우뿐이다 (계획서 0.6).
 *
 * @param blockedReason null이 아니면 선택을 보류하고 안내 문구를 표시한다.
 */
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
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes

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
            Text(
                stringResource(R.string.model_selection_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Column(modifier = Modifier.selectableGroup()) {
                ModelOptionRow(
                    label = stringResource(R.string.model_gemma_name),
                    description = stringResource(R.string.model_gemma_desc),
                    sizeLabel = stringResource(R.string.model_gemma_size),
                    status = gemmaStatus,
                    selected = selectedModel == AppModel.GEMMA,
                    enabled = !isTransitioning && blockedReason == null,
                    onClick = { onModelSelected(AppModel.GEMMA) }
                )
                Spacer(Modifier.height(8.dp))
                ModelOptionRow(
                    label = stringResource(R.string.model_qwen_name),
                    description = stringResource(R.string.model_qwen_desc),
                    sizeLabel = stringResource(R.string.model_qwen_size),
                    status = qwenStatus,
                    selected = selectedModel == AppModel.QWEN3,
                    enabled = !isTransitioning && blockedReason == null,
                    onClick = { onModelSelected(AppModel.QWEN3) }
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
}

/**
 * 추론(Thinking) 모드 토글 카드.
 *
 * 학년과 무관하게 사용자가 직접 켜고 끈다. 켜면 답을 내기 전에 단계를 따져 생각하므로
 * 정확도는 올라가지만 답변이 느려지고 토큰을 더 사용한다.
 */
@Composable
fun ThinkingModeCard(
    thinkingMode: Boolean,
    onThinkingModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
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
                    stringResource(R.string.settings_thinking_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Switch(checked = thinkingMode, onCheckedChange = onThinkingModeChange)
        }
    }
}

@Composable
private fun ModelOptionRow(
    label: String,
    description: String,
    sizeLabel: String,
    status: ModelDownloadStatus,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes

    Surface(
        shape = shapes.large,
        color = if (selected) colors.primaryContainer else colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$sizeLabel · ${statusLabel(status)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
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