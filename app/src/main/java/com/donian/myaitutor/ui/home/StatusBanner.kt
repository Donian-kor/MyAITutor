package com.donian.myaitutor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.R

/**
 * 상단에 표시되는 상태/안내 메시지. 필요 시 "다시 시도" 버튼을 함께 노출한다.
 */
@Composable
internal fun StatusBanner(
    message: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    if (message == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colorResource(id = R.color.status_banner_background))
            .padding(start = 10.dp, top = 5.dp, bottom = 5.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colorResource(id = R.color.status_banner_text)
        )
        if (canRetry) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.btn_retry)) }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_dismiss)) }
    }
}