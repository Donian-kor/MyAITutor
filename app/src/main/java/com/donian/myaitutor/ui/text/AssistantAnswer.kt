package com.donian.myaitutor.ui.text

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.latex.renderer.LatexAutoWrap
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme

@Composable
internal fun AssistantAnswer(text: String, modifier: Modifier = Modifier) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 34.sp)
    val mathConfig = LatexConfig(
        fontSize = textStyle.fontSize,
        theme = LatexTheme.material3()
    )
    val measurer = rememberLatexMeasurer(mathConfig)
    val isDarkTheme = isSystemInDarkTheme()
    val parsed = remember(text) { parseAnswerWithSuggestions(text) }
    val segments = remember(parsed.mainText) { parseAnswerSegments(parsed.mainText) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        var index = 0
        while (index < segments.size) {
            val segment = segments[index]
            if (segment is AnswerSegment.Formula && segment.display) {
                LatexAutoWrap(
                    latex = segment.latex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    config = mathConfig,
                    isDarkTheme = isDarkTheme
                )
                index++
            } else {
                val inlineContent = mutableMapOf<String, InlineTextContent>()
                val annotatedText = buildAnnotatedString {
                    while (index < segments.size) {
                        when (val current = segments[index]) {
                            is AnswerSegment.Text -> appendMarkdown(current.value)
                            is AnswerSegment.Formula -> {
                                if (current.display) return@buildAnnotatedString
                                val id = "formula_$index"
                                val rendered = measurer.inlineContent(
                                    latex = current.latex,
                                    config = mathConfig,
                                    isDarkTheme = isDarkTheme
                                )
                                if (rendered == null) {
                                    append(current.source)
                                } else {
                                    appendInlineContent(id, current.latex)
                                    inlineContent[id] = rendered
                                }
                            }
                        }
                        index++
                    }
                }
                Text(
                    text = annotatedText,
                    inlineContent = inlineContent,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}