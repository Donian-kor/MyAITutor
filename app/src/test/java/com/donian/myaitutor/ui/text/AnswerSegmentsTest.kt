package com.donian.myaitutor.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerSegmentsTest {

    @Test
    fun `수식이 없으면 전체가 Text 세그먼트 하나이다`() {
        val segments = parseAnswerSegments("답은 3입니다.")
        assertEquals(1, segments.size)
        assertEquals(AnswerSegment.Text("답은 3입니다."), segments[0])
    }

    @Test
    fun `인라인 수식은 Formula 세그먼트로 분리된다`() {
        val input = "결과는 ${'$'}x^2${'$'}입니다."
        val segments = parseAnswerSegments(input)
        assertEquals(3, segments.size)
        assertEquals(AnswerSegment.Text("결과는 "), segments[0])
        assertEquals(
            AnswerSegment.Formula("x^2", display = false, source = "${'$'}x^2${'$'}"),
            segments[1]
        )
        assertEquals(AnswerSegment.Text("입니다."), segments[2])
    }

    @Test
    fun `블록 수식은 display=true 로 분리된다`() {
        val input = "${'$'}${'$'}x+1${'$'}${'$'}"
        val segments = parseAnswerSegments(input)
        assertEquals(1, segments.size)
        assertEquals(
            AnswerSegment.Formula("x+1", display = true, source = "${'$'}${'$'}x+1${'$'}${'$'}"),
            segments[0]
        )
    }

    @Test
    fun `수식이 섞인 문장에서 순서를 유지한다`() {
        val input = "A ${'$'}a${'$'} B ${'$'}${'$'}b${'$'}${'$'} C"
        val segments = parseAnswerSegments(input)
        assertEquals(5, segments.size)
        assertEquals(AnswerSegment.Text("A "), segments[0])
        assertEquals(
            AnswerSegment.Formula("a", display = false, source = "${'$'}a${'$'}"),
            segments[1]
        )
        assertEquals(AnswerSegment.Text(" B "), segments[2])
        assertEquals(
            AnswerSegment.Formula("b", display = true, source = "${'$'}${'$'}b${'$'}${'$'}"),
            segments[3]
        )
        assertEquals(AnswerSegment.Text(" C"), segments[4])
    }

    @Test
    fun `빈 문자열은 빈 목록을 반환한다`() {
        val segments = parseAnswerSegments("")
        assertEquals(0, segments.size)
    }
}
