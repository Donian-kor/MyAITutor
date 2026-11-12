package com.donian.myaitutor.ui.text

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme

/**
 * 인라인 LaTeX 수식과 마크다운 서식이 섞인 텍스트를 Compose Text로 렌더링합니다.
 * 선택지 칩, 안내 문구 등 수식이 포함될 수 있는 모든 UI Label에서 사용할 수 있습니다.
 */
@Composable
internal fun FormulaText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current
) {
    val mathConfig = LatexConfig(
        fontSize = style.fontSize,
        theme = LatexTheme.material3()
    )
    val measurer = rememberLatexMeasurer(mathConfig)
    val isDarkTheme = isSystemInDarkTheme()
    val segments = remember(text) { parseAnswerSegments(text) }

    val inlineContent = remember(text, isDarkTheme) { mutableMapOf<String, InlineTextContent>() }
    val annotatedText = remember(text, isDarkTheme) {
        buildAnnotatedString {
            segments.forEachIndexed { index, segment ->
                when (segment) {
                    is AnswerSegment.Text -> appendMarkdown(segment.value)
                    is AnswerSegment.Formula -> {
                        val id = "formula_$index"
                        val rendered = measurer.inlineContent(
                            latex = segment.latex,
                            config = mathConfig,
                            isDarkTheme = isDarkTheme
                        )
                        if (rendered == null) {
                            append(segment.source)
                        } else {
                            appendInlineContent(id, segment.latex)
                            inlineContent[id] = rendered
                        }
                    }
                }
            }
        }
    }

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        modifier = modifier,
        style = style,
        color = color
    )
}