package com.donian.myaitutor.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.donian.myaitutor.R
import com.donian.myaitutor.ui.text.FormulaText

/**
 * 첫 답변 뒤에 표시되는 맞춤형 후속 설명 선택 칩.
 * AI가 생성한 가변 개수(최대 4개)의 맞춤 추천 질문 및 "직접 입력" 칩을 표시한다.
 * 선택지 내부의 인라인 LaTeX 수학 수식($...$)도 FormulaText로 렌더링한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FollowUpChips(
    suggestions: List<String>,
    visible: Boolean,
    isAnalyzing: Boolean,
    inputFocusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    onSendChoice: (String) -> Unit
) {
    if (!visible) return
    Text(
        stringResource(R.string.followup_prompt_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (suggestions.isNotEmpty()) {
            suggestions.forEach { prompt ->
                AssistChip(
                    onClick = { onSendChoice(prompt) },
                    label = {
                        FormulaText(
                            text = prompt,
                            modifier = Modifier.padding(vertical = 4.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    enabled = !isAnalyzing
                )
            }
        } else {
            // AI 동적 제안 파싱 전 또는 폴백용 기본 선택지
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
                    label = {
                        FormulaText(
                            text = choice.label,
                            modifier = Modifier.padding(vertical = 4.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    enabled = !isAnalyzing
                )
            }
            return@FlowRow
        }

        // 맞춤 추천 질문 끝에 항상 '직접 입력' 칩 제공
        AssistChip(
            onClick = {
                inputFocusRequester.requestFocus()
                keyboardController?.show()
            },
            label = {
                Text(
                    stringResource(R.string.followup_custom_input),
                    modifier = Modifier.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            enabled = !isAnalyzing
        )
    }
}