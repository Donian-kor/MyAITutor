package com.donian.myaitutor.ui.text

internal sealed interface AnswerSegment {
    data class Text(val value: String) : AnswerSegment
    data class Formula(val latex: String, val display: Boolean, val source: String) : AnswerSegment
}

internal data class ParsedAnswerWithSuggestions(
    val mainText: String,
    val suggestions: List<String>
)

internal const val SUGGESTIONS_TAG = "[SUGGESTIONS]"

internal fun parseAnswerWithSuggestions(fullText: String): ParsedAnswerWithSuggestions {
    if (!fullText.contains(SUGGESTIONS_TAG)) {
        return ParsedAnswerWithSuggestions(mainText = fullText.trimEnd(), suggestions = emptyList())
    }

    val lastIndex = fullText.lastIndexOf(SUGGESTIONS_TAG)
    val latestSuggestionsPart = fullText.substring(lastIndex + SUGGESTIONS_TAG.length)

    // 최근 [SUGGESTIONS] 블록에서만 제안 질문 추출
    val suggestions = latestSuggestionsPart.lineSequence()
        .map { it.trim() }
        .takeWhile { line ->
            !line.startsWith("학생:") && !line.startsWith("AI 답변:")
        }
        .map { line ->
            when {
                line.startsWith("-") -> line.substring(1).trim()
                line.startsWith("*") -> line.substring(1).trim()
                line.startsWith("•") -> line.substring(1).trim()
                else -> line
            }
        }
        .filter { it.isNotBlank() && !it.startsWith("(") && !it.startsWith("[") }
        .toList()

    // 이전 답변들의 모든 [SUGGESTIONS] 블록을 본문 텍스트에서 제거하여 중복/누수 방지
    val cleanMainText = stripAllSuggestionsBlocks(fullText)

    return ParsedAnswerWithSuggestions(mainText = cleanMainText, suggestions = suggestions)
}

private fun stripAllSuggestionsBlocks(fullText: String): String {
    val sb = StringBuilder()
    var cursor = 0
    while (cursor < fullText.length) {
        val tagIndex = fullText.indexOf(SUGGESTIONS_TAG, cursor)
        if (tagIndex < 0) {
            sb.append(fullText.substring(cursor))
            break
        }
        sb.append(fullText.substring(cursor, tagIndex).trimEnd())

        val rest = fullText.substring(tagIndex + SUGGESTIONS_TAG.length)
        val nextTurnIndexInRest = findNextTurnStart(rest)
        if (nextTurnIndexInRest < 0) {
            cursor = fullText.length
        } else {
            cursor = tagIndex + SUGGESTIONS_TAG.length + nextTurnIndexInRest
            sb.append("\n\n")
        }
    }
    return sb.toString().trimEnd()
}

private fun findNextTurnStart(text: String): Int {
    val studentIdx = text.indexOf("\n학생:")
    val aiIdx = text.indexOf("\nAI 답변:")
    val idxs = listOf(studentIdx, aiIdx).filter { it >= 0 }
    return if (idxs.isEmpty()) -1 else idxs.minOrNull()!! + 1
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