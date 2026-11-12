package com.donian.myaitutor.ui.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * 모델이 출력한 Markdown을 화면 서식으로 변환합니다.
 * 지원: **굵게**, 제목(#), 목록(-, *, +). 지원하지 않는 기호는 그대로 둡니다.
 */
private val MarkdownHeadingPattern = Regex("""^[ \t]*#{1,6}[ \t]+""")
private val MarkdownBulletPattern = Regex("""^[ \t]*[-*+][ \t]+""")

internal fun AnnotatedString.Builder.appendMarkdown(text: String) {
    var lineStart = 0
    while (true) {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        appendMarkdownLine(text.substring(lineStart, lineEnd))
        if (lineEnd == text.length) return
        append('\n')
        lineStart = lineEnd + 1
    }
}

internal fun AnnotatedString.Builder.appendMarkdownLine(line: String) {
    val heading = MarkdownHeadingPattern.find(line)
    if (heading != null) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            appendInlineMarkdown(line.substring(heading.range.last + 1))
        }
        return
    }
    val bullet = MarkdownBulletPattern.find(line)
    if (bullet != null) {
        append("• ")
        appendInlineMarkdown(line.substring(bullet.range.last + 1))
        return
    }
    appendInlineMarkdown(line)
}

internal fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    var cursor = 0
    while (cursor < text.length) {
        val open = text.indexOf("**", cursor)
        if (open < 0) {
            append(text.substring(cursor))
            return
        }
        append(text.substring(cursor, open))
        val close = text.indexOf("**", open + 2)
        if (close < 0) {
            // 스트리밍 중 닫는 기호가 아직 오지 않은 경우: 기호는 숨기고 굵게 이어 씁니다.
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(text.substring(open + 2))
            }
            return
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(open + 2, close))
        }
        cursor = close + 2
    }
}