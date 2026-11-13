package com.donian.myaitutor.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextPostProcessorTest {
    @Test
    fun `전각 문자와 불필요한 공백을 정규화한다`() {
        val normalized = OcrTextPostProcessor.normalizeText("　１＋２　＝　３  ")

        assertEquals("1+2 = 3", normalized)
    }

    @Test
    fun `숫자 문맥에서만 O와 l을 보정한다`() {
        assertEquals("2025", OcrTextPostProcessor.normalizeText("2O25"))
        assertEquals("123", OcrTextPostProcessor.normalizeText("l23"))
        // 화학식은 건드리지 않습니다.
        assertEquals("CO2", OcrTextPostProcessor.normalizeText("CO2"))
        assertEquals("H2O", OcrTextPostProcessor.normalizeText("H2O"))
        assertEquals("1O", OcrTextPostProcessor.normalizeText("1O"))
    }

    @Test
    fun `기호만 있는 블록과 저신뢰 한 글자 블록을 노이즈로 판단한다`() {
        assertTrue(OcrTextPostProcessor.isNoise("---", 0.9f))
        assertTrue(OcrTextPostProcessor.isNoise("=", 0.9f))
        assertTrue(OcrTextPostProcessor.isNoise("가", 0.2f))
        assertFalse(OcrTextPostProcessor.isNoise("가", 0.9f))
        assertFalse(OcrTextPostProcessor.isNoise("문제", 0.2f))
        assertFalse(OcrTextPostProcessor.isNoise("12", 0.2f))
        // 선택지 번호로 쓰이는 원문자 기호는 유지합니다.
        assertFalse(OcrTextPostProcessor.isNoise("①", 0.9f))
    }

    @Test
    fun `겹치는 중복 블록은 신뢰도가 높은 쪽만 남긴다`() {
        val low = candidate("1+2", 0.4f, 10, 10, 110, 40)
        val high = candidate("1+2", 0.95f, 12, 12, 108, 38)

        val processed = OcrTextPostProcessor.process(listOf(low, high))

        assertEquals(1, processed.size)
        assertEquals(0.95f, processed.first().confidence)
    }

    @Test
    fun `좌표가 있으면 행 단위로 위에서 아래, 왼쪽에서 오른쪽으로 정렬한다`() {
        val rightTop = candidate("A", 0.9f, 100, 10, 200, 30)
        val leftBottom = candidate("B", 0.9f, 10, 100, 60, 120)
        val leftTop = candidate("C", 0.9f, 10, 10, 50, 30)

        val processed = OcrTextPostProcessor.process(listOf(rightTop, leftBottom, leftTop))

        assertEquals(listOf("C", "A", "B"), processed.map { it.text })
    }

    @Test
    fun `좌표가 없으면 SDK가 준 순서를 유지한다`() {
        val first = OcrTextCandidate(text = "첫째", confidence = 0.9f)
        val second = OcrTextCandidate(text = "둘째", confidence = 0.9f)

        val processed = OcrTextPostProcessor.process(listOf(first, second))

        assertEquals(listOf("첫째", "둘째"), processed.map { it.text })
    }

    @Test
    fun `빈 문자열과 공백뿐인 블록은 제거한다`() {
        val blank = OcrTextCandidate(text = "   ", confidence = 0.9f)
        val text = candidate("문제", 0.9f, 0, 0, 10, 10)

        val processed = OcrTextPostProcessor.process(listOf(blank, text))

        assertEquals(listOf("문제"), processed.map { it.text })
    }

    private fun candidate(
        text: String,
        confidence: Float,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) = OcrTextCandidate(
        text = text,
        confidence = confidence,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        hasGeometry = true
    )
}
