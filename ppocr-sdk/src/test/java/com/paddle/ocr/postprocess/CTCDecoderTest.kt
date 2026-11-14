package com.paddle.ocr.postprocess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CTCDecoder.decode]의 greedy 디코딩을 검증한다.
 *
 * 인자가 FloatArray / LongArray / List<String> 뿐이라 Android·OpenCV 없이 JVM에서 그대로 실행된다.
 * 규약은 다음과 같다.
 * - shape = [batch, timeSteps, numClasses]
 * - 각 스텝에서 argmax를 고르고, 인덱스 0은 blank
 * - blank는 건너뛰고, 직전과 같은 인덱스가 연속되면 한 번만 낸다
 * - 신뢰도는 실제로 채택된 글자들의 확률 평균
 */
class CTCDecoderTest {

    /** 각 스텝의 argmax가 [indices]가 되도록 one-hot에 가까운 logits을 만든다. */
    private fun logitsOf(indices: List<Int>, numClasses: Int, maxValue: Float = 1f): FloatArray {
        val out = FloatArray(indices.size * numClasses)
        for ((t, idx) in indices.withIndex()) {
            out[t * numClasses + idx] = maxValue
        }
        return out
    }

    private fun shapeOf(batch: Int, timeSteps: Int, numClasses: Int) =
        longArrayOf(batch.toLong(), timeSteps.toLong(), numClasses.toLong())

    @Test
    fun `빈 입력은 빈 결과 목록을 준다`() {
        // batch = 0
        assertTrue(CTCDecoder.decode(FloatArray(0), shapeOf(0, 3, 4), listOf("a", "b", "c")).isEmpty())
    }

    @Test
    fun `시간 스텝이 0이면 빈 문자열과 신뢰도 0을 준다`() {
        val result = CTCDecoder.decode(FloatArray(0), shapeOf(1, 0, 4), listOf("a", "b", "c"))

        assertEquals(1, result.size)
        assertEquals("", result.first().first)
        assertEquals(0f, result.first().second)
    }

    @Test
    fun `스텝이 하나면 그 글자 하나만 낸다`() {
        val result = CTCDecoder.decode(logitsOf(listOf(1), numClasses = 3), shapeOf(1, 1, 3), listOf("a", "b"))

        // 인덱스 1은 blank이므로 character_list[0] = "a"에 대응한다.
        assertEquals(listOf("a" to 1f), result)
    }

    @Test
    fun `blank 인덱스 0은 무시한다`() {
        val result = CTCDecoder.decode(logitsOf(listOf(0, 0, 0), numClasses = 3), shapeOf(1, 3, 3), listOf("a", "b"))

        assertEquals("", result.single().first)
        assertEquals(0f, result.single().second)
    }

    @Test
    fun `연속으로 같은 인덱스가 나오면 한 글자만 낸다`() {
        val result = CTCDecoder.decode(
            logitsOf(listOf(2, 2, 2, 2), numClasses = 3),
            shapeOf(1, 4, 3),
            listOf("a", "b"),
        )

        assertEquals("b", result.single().first)
    }

    @Test
    fun `blank를 사이에 둔 같은 글자는 두 번 낸다`() {
        val result = CTCDecoder.decode(
            logitsOf(listOf(1, 0, 1), numClasses = 3),
            shapeOf(1, 3, 3),
            listOf("a"),
        )

        assertEquals("aa", result.single().first)
    }

    @Test
    fun `배치가 여러 개면 배치마다 따로 디코딩한다`() {
        // 배치 0: [1, 1, 0, 1] → "aa", 배치 1: [0, 0, 0] → ""
        val batch0 = logitsOf(listOf(1, 1, 0, 1), numClasses = 3)
        val batch1 = logitsOf(listOf(0, 0, 0, 0), numClasses = 3)
        val result = CTCDecoder.decode(
            batch0 + batch1,
            shapeOf(2, 4, 3),
            listOf("a", "b"),
        )

        assertEquals(2, result.size)
        assertEquals("aa", result[0].first)
        assertEquals("", result[1].first)
    }

    @Test
    fun `허용 인덱스 범위 밖 글자는 건너뛴다`() {
        // numClasses = 4 → 최대 인덱스 3, character_list 크기 2 → charIdx 2 는 범위 밖
        val result = CTCDecoder.decode(
            logitsOf(listOf(3, 3), numClasses = 4),
            shapeOf(1, 2, 4),
            listOf("a", "b"),
        )

        assertEquals("", result.single().first)
        assertEquals(0f, result.single().second)
    }

    @Test
    fun `character_list 마지막 원소는 경계 안쪽으로 정상 출력한다`() {
        // charIdx = size - 1 == 1 (문자 "b")
        val result = CTCDecoder.decode(
            logitsOf(listOf(2), numClasses = 4),
            shapeOf(1, 1, 4),
            listOf("a", "b"),
        )

        assertEquals("b", result.single().first)
    }

    @Test
    fun `신뢰도는 채택된 글자들의 확률 평균이다`() {
        // t0: idx 1 확률 0.8 (채택), t1: idx 0 blank 확률 0.9 (버림)
        val output = floatArrayOf(0.6f, 0.8f, 0.9f, 0.7f)
        val result = CTCDecoder.decode(output, shapeOf(1, 2, 2), listOf("a"))

        assertEquals("a", result.single().first)
        assertEquals(0.8f, result.single().second, 1e-6f)
    }

    @Test
    fun `여러 글자의 신뢰도는 각각 평균된다`() {
        // t0: idx1 1.0, t1: idx2 0.5, t2: idx1 1.0 → "ab" (t0/t2는 서로 떨어져 있어 둘 다 채택)
        val output = floatArrayOf(
            0f, 1.0f, 0f, // idx 1
            0f, 0.2f, 0.5f, // idx 2
            0f, 1.0f, 0f, // idx 1
        )
        val result = CTCDecoder.decode(output, shapeOf(1, 3, 3), listOf("a", "b"))

        assertEquals("aba", result.single().first)
        assertEquals((1.0f + 0.5f + 1.0f) / 3f, result.single().second, 1e-6f)
    }

    @Test
    fun `최댓값이 같으면 앞쪽 인덱스를 고른다`() {
        // 모든 클래스 확률이 동일 → argmax는 인덱스 0 (blank)
        val result = CTCDecoder.decode(FloatArray(6) { 0.5f }, shapeOf(1, 2, 3), listOf("a", "b"))

        assertEquals("", result.single().first)
        assertEquals(0f, result.single().second)
    }

    @Test
    fun `character_list가 비어 있어도 예외 없이 빈 문자열을 준다`() {
        val result = CTCDecoder.decode(logitsOf(listOf(1), numClasses = 3), shapeOf(1, 1, 3), emptyList())

        assertEquals("", result.single().first)
        assertEquals(0f, result.single().second)
    }
}