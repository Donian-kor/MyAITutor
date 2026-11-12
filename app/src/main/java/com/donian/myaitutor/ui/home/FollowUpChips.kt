package com.donian.myaitutor.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.dp

/**
 * 첫 답변 뒤에 표시되는 후속 설명 선택 칩.
 * "직접 입력"을 누르면 입력창으로 포커스를 옮기고, 나머지는 선택한 프롬프트로 질문을 보낸다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FollowUpChips(
    visible: Boolean,
    isAnalyzing: Boolean,
    inputFocusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    onSendChoice: (String) -> Unit
) {
    if (!visible) return
    Text(
        "다음에 알고 싶은 내용을 선택하세요",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        FollowUpChoices.forEach { choice ->
            AssistChip(
                onClick = {
                    val prompt = choice.prompt
                    if (prompt == null) {
                        inputFocusRequester.requestFocus()
                        keyboardController?.show()
                    } else {
                        onSendChoice(prompt)
                    }
                },
                label = { Text(choice.label) },
                enabled = !isAnalyzing
            )
        }
    }
}