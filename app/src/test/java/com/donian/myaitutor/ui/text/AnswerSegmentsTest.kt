package com.donian.myaitutor.ui.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `SUGGESTIONS 태그가 없으면 본문만 반환하고 추천 목록은 비어있다`() {
        val parsed = parseAnswerWithSuggestions("이 문제는 피타고라스 정리 문제입니다.")
        assertEquals("이 문제는 피타고라스 정리 문제입니다.", parsed.mainText)
        assertTrue(parsed.suggestions.isEmpty())
    }

    @Test
    fun `상황에 따라 2개의 추천 질문이 파싱된다`() {
        val input = """
            이 문제의 답은 5입니다.
            
            [SUGGESTIONS]
            - 피타고라스 정리 적용 원리가 궁금해요
            - 비슷 문제 하나 더 풀어볼래요
        """.trimIndent()
        val parsed = parseAnswerWithSuggestions(input)
        assertEquals("이 문제의 답은 5입니다.", parsed.mainText)
        assertEquals(2, parsed.suggestions.size)
        assertEquals("피타고라스 정리 적용 원리가 궁금해요", parsed.suggestions[0])
        assertEquals("비슷 문제 하나 더 풀어볼래요", parsed.suggestions[1])
    }

    @Test
    fun `상황에 따라 4개의 추천 질문이 파싱된다`() {
        val input = """
            해설입니다.
            
            [SUGGESTIONS]
            - 자세한 유도 과정
            - 개념 설명
            - 검산 단계
            - 공식 암기 팁
        """.trimIndent()
        val parsed = parseAnswerWithSuggestions(input)
        assertEquals("해설입니다.", parsed.mainText)
        assertEquals(4, parsed.suggestions.size)
        assertEquals("자세한 유도 과정", parsed.suggestions[0])
        assertEquals("개념 설명", parsed.suggestions[1])
        assertEquals("검산 단계", parsed.suggestions[2])
        assertEquals("공식 암기 팁", parsed.suggestions[3])
    }

    @Test
    fun `다중 대화 세션에서 이전 답변의 SUGGESTIONS 태그와 옵션은 본문에서 지워지고 최신 옵션만 파싱된다`() {
        val input = """
            첫번째 답변입니다.

            [SUGGESTIONS]
            - 옛날 질문 1
            - 옛날 질문 2

            학생: 옛날 질문 1

            AI 답변: 두번째 답변입니다.

            [SUGGESTIONS]
            - 최신 질문 A
            - 최신 질문 B
        """.trimIndent()

        val parsed = parseAnswerWithSuggestions(input)
        val expectedMainText = """
            첫번째 답변입니다.

            학생: 옛날 질문 1

            AI 답변: 두번째 답변입니다.
        """.trimIndent()

        assertEquals(expectedMainText, parsed.mainText)
        assertEquals(2, parsed.suggestions.size)
        assertEquals("최신 질문 A", parsed.suggestions[0])
        assertEquals("최신 질문 B", parsed.suggestions[1])
    }
}
