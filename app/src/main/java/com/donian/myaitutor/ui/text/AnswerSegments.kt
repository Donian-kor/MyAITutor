package com.donian.myaitutor.ui.text

internal sealed interface AnswerSegment {
    data class Text(val value: String) : AnswerSegment
    data class Formula(val latex: String, val display: Boolean, val source: String) : AnswerSegment
}

internal val MathDelimiterPattern = Regex(
    """(?s)\$\$(.+?)\$\$|\\\[(.+?)\\\]|\\\((.+?)\\\)|(?<!\\)\$(?!\$)(.+?)(?<!\\)\$(?!\$)"""
)

internal fun parseAnswerSegments(answer: String): List<AnswerSegment> = buildList {
    var cursor = 0
    MathDelimiterPattern.findAll(answer).forEach { match ->
        if (match.range.first > cursor) {
            add(AnswerSegment.Text(answer.substring(cursor, match.range.first)))
        }
        val displayLatex = match.groups[1]?.value ?: match.groups[2]?.value
        val inlineLatex = match.groups[3]?.value ?: match.groups[4]?.value
        if (displayLatex != null) {
            add(AnswerSegment.Formula(displayLatex.trim(), display = true, source = match.value))
        } else if (inlineLatex != null) {
            add(AnswerSegment.Formula(inlineLatex.trim(), display = false, source = match.value))
        }
        cursor = match.range.last + 1
    }
    if (cursor < answer.length) add(AnswerSegment.Text(answer.substring(cursor)))
}