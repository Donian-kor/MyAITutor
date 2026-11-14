package com.paddle.ocr.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MathUtils]의 반올림 로직을 검증한다.
 * DB 후처리에서 좌표를 정수 픽셀로 스케일할 때 쓰이는 "반값은 짝수로" 규칙이
 * 일반 반올림과 어떻게 다른지가 핵심이다.
 */
class MathUtilsTest {

    @Test
    fun `0과 1 사이는 내림한다`() {
        assertEquals(0, MathUtils.roundHalfToEven(0.4))
        assertEquals(0, MathUtils.roundHalfToEven(0.49))
        assertEquals(1, MathUtils.roundHalfToEven(0.6))
        assertEquals(1, MathUtils.roundHalfToEven(0.99))
    }

    @Test
    fun `정확히 절반이면 짝수 쪽으로 간다`() {
        // 0.5 → 짝수 0
        assertEquals(0, MathUtils.roundHalfToEven(0.5))
        // 1.5 → 짝수 2
        assertEquals(2, MathUtils.roundHalfToEven(1.5))
        // 2.5 → 짝수 2 (일반 반올림이면 3)
        assertEquals(2, MathUtils.roundHalfToEven(2.5))
        // 3.5 → 짝수 4
        assertEquals(4, MathUtils.roundHalfToEven(3.5))
    }

    @Test
    fun `음수도 같은 규칙을 따른다`() {
        assertEquals(-1, MathUtils.roundHalfToEven(-0.6))
        // -0.5 → 짝수 0
        assertEquals(0, MathUtils.roundHalfToEven(-0.5))
        // -1.5 → 짝수 -2
        assertEquals(-2, MathUtils.roundHalfToEven(-1.5))
        // -2.5 → 짝수 -2
        assertEquals(-2, MathUtils.roundHalfToEven(-2.5))
        assertEquals(-3, MathUtils.roundHalfToEven(-2.6))
    }

    @Test
    fun `32의 배수로 내삽할 때 사용되는 패턴을 검증한다`() {
        // det 전처리에서 쓰이는 newH / 32.0 → round → *32 패턴.
        assertEquals(64, MathUtils.roundHalfToEven(64 / 32.0) * 32)
        // 48 / 32 = 1.5 → 짝수 2 → 64
        assertEquals(64, MathUtils.roundHalfToEven(48 / 32.0) * 32)
        // 16 / 32 = 0.5 → 짝수 0 → 0 (호출부에서 max(..., 32)로 보정한다)
        assertEquals(0, MathUtils.roundHalfToEven(16 / 32.0) * 32)
        // 80 / 32 = 2.5 → 짝수 2 → 64
        assertEquals(64, MathUtils.roundHalfToEven(80 / 32.0) * 32)
    }

    @Test
    fun `경계값에서도 죽지 않는다`() {
        assertEquals(Int.MAX_VALUE, MathUtils.roundHalfToEven(Int.MAX_VALUE.toDouble()))
        assertEquals(Int.MIN_VALUE, MathUtils.roundHalfToEven(Int.MIN_VALUE.toDouble()))
        // 아주 작은 양수와 0
        assertEquals(0, MathUtils.roundHalfToEven(Double.MIN_VALUE))
        assertEquals(0, MathUtils.roundHalfToEven(0.0))
    }
}