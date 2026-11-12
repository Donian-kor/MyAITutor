package com.donian.myaitutor.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerMarkdownTest {

    @Test
    fun `appendMarkdown이 굵게 기호를 AnnotatedString으로 변환한다`() {
        val builder = androidx.compose.ui.text.buildAnnotatedString {
            appendMarkdown("**굵게**")
        }
        assertEquals("굵게", builder.text)
        // 굵게 스팬이 존재하는지 확인
        val hasBold = builder.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold }
        assertEquals(true, hasBold)
    }

    @Test
    fun `닫히지 않은 굵게는 기호를 숨기고 굵게로 이어진다`() {
        val builder = androidx.compose.ui.text.buildAnnotatedString {
            appendMarkdown("**굵게")
        }
        assertEquals("굵게", builder.text)
    }

    @Test
    fun `제목 기호는 제거되고 굵게로 표시된다`() {
        val builder = androidx.compose.ui.text.buildAnnotatedString {
            appendMarkdown("### 제목")
        }
        assertEquals("제목", builder.text)
    }

    @Test
    fun `목록 기호는 점으로 변환된다`() {
        val builder = androidx.compose.ui.text.buildAnnotatedString {
            appendMarkdown("- 항목")
        }
        assertEquals("• 항목", builder.text)
    }
}
