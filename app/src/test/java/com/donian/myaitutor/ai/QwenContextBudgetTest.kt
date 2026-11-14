package com.donian.myaitutor.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [QwenContextBudget]의 순수 계산 로직을 검증한다.
 * 실제 토크나이저 대신 문자 수 기반 추정치를 쓰므로 값은 CHARS_PER_TOKEN 기준으로 계산한다.
 */
class QwenContextBudgetTest {

    private fun textOfTokens(tokens: Int): String = "가".repeat(tokens * QwenContextBudget.CHARS_PER_TOKEN.toInt())

    @Test
    fun `빈 문자열은 0토큰이다`() {
        assertEquals(0, QwenContextBudget.estimateTokens(""))
    }

    @Test
    fun `문자열 길이에 비례해 토큰을 추정한다`() {
        // 30자 → 30 / 3.0 = 10토큰
        assertEquals(10, QwenContextBudget.estimateTokens("가".repeat(30)))
        // 나머지가 있으면 올림한다. 31자 → 11토큰
        assertEquals(11, QwenContextBudget.estimateTokens("가".repeat(31)))
    }

    @Test
    fun `입력 예산은 컨텍스트에서 출력과 시스템 지시문과 여유분을 뺀 값이다`() {
        val budget = QwenContextBudget.inputBudgetTokens(
            contextTokens = 2_048,
            outputTokens = 512,
            systemInstructionTokens = 1_000,
            safetyMarginTokens = 64
        )

        assertEquals(472, budget)
    }

    @Test
    fun `차감 후 예산이 음수가 되면 0으로 보정한다`() {
        val budget = QwenContextBudget.inputBudgetTokens(
            contextTokens = 2_048,
            outputTokens = 512,
            systemInstructionTokens = 5_000,
            safetyMarginTokens = 64
        )

        assertEquals(0, budget)
    }

    @Test
    fun `예산 안이면 모든 블록을 그대로 전달한다`() {
        val parts = listOf("짧은 문제", "지시문")

        val result = QwenContextBudget.fit(parts, budgetTokens = 100)

        assertEquals(parts, result.parts)
        assertFalse(result.truncated)
        assertFalse(result.overflow)
    }

    @Test
    fun `예산을 넘는 블록은 뒤쪽을 자르고 생략 표시를 붙인다`() {
        val long = textOfTokens(200)
        val budget = 50

        val result = QwenContextBudget.fit(listOf(long), budgetTokens = budget)

        val fitted = result.parts.single()
        assertTrue(result.truncated)
        assertFalse(result.overflow)
        assertTrue(fitted.endsWith(QwenContextBudget.OMITTED_MARKER))
        assertTrue(QwenContextBudget.estimateTokens(fitted) <= budget)
        // 앞부분이 보존되어야 한다.
        assertTrue(long.startsWith(fitted.removeSuffix(QwenContextBudget.OMITTED_MARKER)))
    }

    @Test
    fun `앞 블록을 먼저 채우고 남은 예산을 뒤 블록에 쓴다`() {
        val first = textOfTokens(30)
        val second = textOfTokens(30)
        val budget = 40

        val result = QwenContextBudget.fit(listOf(first, second), budgetTokens = budget)

        assertEquals(first, result.parts[0])
        assertTrue(result.parts[1].length < second.length)
        assertTrue(result.truncated)
        assertFalse(result.overflow)
    }

    @Test
    fun `첫 블록조차 담을 수 없으면 오버플로로 알린다`() {
        val result = QwenContextBudget.fit(
            listOf(textOfTokens(100)),
            budgetTokens = QwenContextBudget.OMITTED_MARKER.length / QwenContextBudget.CHARS_PER_TOKEN.toInt()
        )

        assertTrue(result.overflow)
        assertEquals("", result.parts.single())
    }

    @Test
    fun `블록이 비어 있으면 아무것도 하지 않는다`() {
        val result = QwenContextBudget.fit(emptyList(), budgetTokens = 100)

        assertTrue(result.parts.isEmpty())
        assertFalse(result.truncated)
        assertFalse(result.overflow)
    }

    @Test
    fun `0토큰 예산으로 자르면 빈 문자열이 된다`() {
        assertEquals("", QwenContextBudget.truncateToTokens("가나다라", maxTokens = 0))
    }
}