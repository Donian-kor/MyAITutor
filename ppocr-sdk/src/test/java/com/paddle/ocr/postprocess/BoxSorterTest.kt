package com.paddle.ocr.postprocess

import android.graphics.PointF
import com.paddle.ocr.model.OCRBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BoxSorter.sortInReadingOrder]의 읽기 순서 정렬을 검증한다.
 *
 * OCRBox 는 android.graphics.PointF 를 담는다. 단위 테스트의 android.jar 는 스텁이라
 * PointF(x, y) 생성자가 필드를 채우지 않으므로(항상 0.0), 공개 필드 x/y 에 직접 대입해 만든다.
 * BoxSorter 는 points[0] 의 x/y 만 읽으므로 이 방법으로 요구 필드를 모두 채울 수 있다.
 * 규약: 우선 위쪽 y, 그 다음 왼쪽 x 순이고, y 차이가 10px 미만이면 같은 행으로 보고 x를 다시 비교한다.
 */
class BoxSorterTest {

    private fun pointF(x: Float, y: Float): PointF = PointF().apply {
        this.x = x
        this.y = y
    }

    /** 좌상단 점이 (x, y)인 20x10 박스를 만든다. 나머지 점은 정렬에 쓰이지 않는다. */
    private fun box(x: Float, y: Float): OCRBox = OCRBox(
        points = listOf(
            pointF(x, y),
            pointF(x + 20f, y),
            pointF(x + 20f, y + 10f),
            pointF(x, y + 10f),
        ),
    )

    private fun topLefts(boxes: List<OCRBox>): List<Pair<Float, Float>> =
        boxes.map { it.points[0].x to it.points[0].y }

    @Test
    fun `빈 목록은 그대로 돌려준다`() {
        val input = emptyList<OCRBox>()

        val result = BoxSorter.sortInReadingOrder(input)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `원소가 하나면 정렬하지 않고 같은 객체를 돌려준다`() {
        val input = listOf(box(10f, 10f))

        val result = BoxSorter.sortInReadingOrder(input)

        assertSame(input, result)
    }

    @Test
    fun `같은 행은 왼쪽에서 오른쪽으로 정렬한다`() {
        val result = BoxSorter.sortInReadingOrder(listOf(box(100f, 10f), box(20f, 10f), box(60f, 10f)))

        assertEquals(listOf(20f to 10f, 60f to 10f, 100f to 10f), topLefts(result))
    }

    @Test
    fun `행이 다르면 위에서 아래로 정렬한다`() {
        val result = BoxSorter.sortInReadingOrder(listOf(box(10f, 80f), box(10f, 20f), box(10f, 50f)))

        assertEquals(listOf(10f to 20f, 10f to 50f, 10f to 80f), topLefts(result))
    }

    @Test
    fun `y 차이가 임계값보다 작으면 같은 행으로 본다`() {
        // y 차 9 (< 10) → 같은 행 → x 순으로
        val result = BoxSorter.sortInReadingOrder(listOf(box(90f, 10f), box(10f, 19f)))

        assertEquals(listOf(10f to 19f, 90f to 10f), topLefts(result))
    }

    @Test
    fun `y 차이가 임계값과 같으면 다른 행으로 본다`() {
        // y 차 10 (== 10) → 행 분리 → 위쪽(10)이 먼저
        val result = BoxSorter.sortInReadingOrder(listOf(box(90f, 20f), box(10f, 10f)))

        assertEquals(listOf(10f to 10f, 90f to 20f), topLefts(result))
    }

    @Test
    fun `아래 행이 더 왼쪽이어도 위 행이 먼저 온다`() {
        val result = BoxSorter.sortInReadingOrder(
            listOf(box(5f, 100f), box(300f, 10f), box(120f, 55f)),
        )

        assertEquals(listOf(300f to 10f, 120f to 55f, 5f to 100f), topLefts(result))
    }

    @Test
    fun `좌표가 완전히 같은 박스는 순서가 유지된다`() {
        val a = box(30f, 30f)
        val b = box(30f, 30f)
        val c = box(30f, 30f)

        val result = BoxSorter.sortInReadingOrder(listOf(a, b, c))

        assertEquals(listOf(a, b, c), result)
    }

    @Test
    fun `내림차순 입력을 오름차순으로 뒤집는다`() {
        val result = BoxSorter.sortInReadingOrder(
            listOf(box(50f, 30f), box(50f, 20f), box(50f, 10f)),
        )

        assertEquals(listOf(50f to 10f, 50f to 20f, 50f to 30f), topLefts(result))
    }

    @Test
    fun `원본 목록은 변경하지 않는다`() {
        val input = listOf(box(90f, 20f), box(10f, 10f))
        val before = input.toList()

        BoxSorter.sortInReadingOrder(input)

        assertEquals(before, input)
    }
}