package com.paddle.ocr.postprocess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.opencv.core.Point

/**
 * [QuadGeometry.orderMinAreaRectPoints]의 점 정렬을 검증한다.
 *
 * org.opencv.core.Point 는 x/y 만 담는 순수 JVM 클래스(네이티브 호출 없음)이므로
 * OpenCV 네이티브 로드 없이 JVM 단위 테스트에서 그대로 쓸 수 있다.
 * 규약: 입력 순서와 무관하게 [왼쪽 위, 오른쪽 위, 오른쪽 아래, 왼쪽 아래] 순으로 돌려준다.
 */
class QuadGeometryTest {

    private fun point(x: Double, y: Double) = Point(x, y)

    private fun ordered(points: Array<Point>): List<Pair<Double, Double>> =
        QuadGeometry.orderMinAreaRectPoints(points).map { it.x to it.y }

    @Test
    fun `뒤섞인 사각형 점을 시계 방향으로 정렬한다`() {
        val points = arrayOf(
            point(48.0, 40.0), // 오른쪽 아래
            point(10.0, 10.0), // 왼쪽 위
            point(50.0, 12.0), // 오른쪽 위
            point(8.0, 38.0), // 왼쪽 아래
        )

        assertEquals(
            listOf(10.0 to 10.0, 50.0 to 12.0, 48.0 to 40.0, 8.0 to 38.0),
            ordered(points),
        )
    }

    @Test
    fun `축에 정렬된 사각형도 같은 규칙으로 정렬한다`() {
        val points = arrayOf(
            point(10.0, 5.0),
            point(0.0, 0.0),
            point(10.0, 0.0),
            point(0.0, 5.0),
        )

        assertEquals(
            listOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 5.0, 0.0 to 5.0),
            ordered(points),
        )
    }

    @Test
    fun `이미 정렬된 입력은 순서가 유지된다`() {
        val points = arrayOf(
            point(0.0, 0.0),
            point(10.0, 0.0),
            point(10.0, 5.0),
            point(0.0, 5.0),
        )

        assertEquals(
            listOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 5.0, 0.0 to 5.0),
            ordered(points),
        )
    }

    @Test
    fun `x 좌표가 모두 같으면 y 비교로 위아래를 가른다`() {
        val points = arrayOf(
            point(5.0, 30.0),
            point(5.0, 30.0),
            point(5.0, 0.0),
            point(5.0, 0.0),
        )

        // x가 전부 같아 sortedBy가 안정 정렬로 [ (5,30), (5,30), (5,0), (5,0) ]이 되면
        // sorted[1].y(30) > sorted[0].y(30) 이 false → topLeft = sorted[1] = (5,30)
        assertEquals(
            listOf(5.0 to 30.0, 5.0 to 0.0, 5.0 to 0.0, 5.0 to 30.0),
            ordered(points),
        )
    }

    @Test
    fun `점이 4개 미만이면 예외가 난다`() {
        assertThrows(IndexOutOfBoundsException::class.java) {
            QuadGeometry.orderMinAreaRectPoints(arrayOf(point(0.0, 0.0), point(1.0, 1.0)))
        }
    }

    @Test
    fun `입력 배열을 변경하지 않는다`() {
        val points = arrayOf(
            point(48.0, 40.0),
            point(10.0, 10.0),
            point(50.0, 12.0),
            point(8.0, 38.0),
        )
        val before = points.toList()

        QuadGeometry.orderMinAreaRectPoints(points)

        assertEquals(before, points.toList())
    }
}