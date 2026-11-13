package com.donian.myaitutor.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.R
import com.donian.myaitutor.model.AppModel
import com.donian.myaitutor.model.ModelDownloadStatus

/**
 * Gemma / Qwen 선택 라디오 카드.
 *
 * 기존 Gemma 모델 카드를 대체하지 않고 그 아래에 별도로 추가된다.
 * 각 모델의 이름·용량·다운로드 상태를 표시하고, 선택을 [onModelSelected]로 위임한다.
 *
 * @param blockedReason null이 아니면 선택을 보류하고 안내 문구를 표시한다 (계획서 0.6).
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