package com.donian.myaitutor.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.R

/**
 * 모델 전환이 2단계 복구까지 실패한 경우의 선택 다이얼로그 (계획서 0.4).
 *
 * 자동 복구(이전 모델 재로드)가 실패해 어떤 모델도 로드되지 않은
 * [com.donian.myaitutor.model.ModelRuntimeState.NO_MODEL_LOADED] 상태에서 표시된다.
 *
 * @param failureMessage 사용자에게 안내할 실패 사유. OCR 원문·사용자 질문을 담지 않는다.
 * @param onRetry "다시 시도" — 실패한 모델을 다시 로드한다.
 * @param onFallbackToGemma "Gemma로 돌아가기" — Gemma를 새로 활성화한다.
 * @param onDismiss "나중에" — 모델 없이 앱을 계속 사용한다(AI 기능 비활성).
 */
@Composable
fun NoModelRecoveryDialog(
    failureMessage: String?,
    onRetry: () -> Unit,
    onFallbackToGemma: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_no_model_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.dialog_no_model_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!failureMessage.isNullOrBlank()) {
                    Text(
                        stringResource(R.string.dialog_no_model_reason_format, failureMessage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.dialog_no_model_retry))
                }
                TextButton(onClick = onFallbackToGemma) {
                    Text(stringResource(R.string.dialog_no_model_fallback_gemma))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_no_model_later))
                }
            }
        }
    )
}