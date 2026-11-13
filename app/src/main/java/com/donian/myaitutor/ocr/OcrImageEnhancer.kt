package com.donian.myaitutor.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.donian.myaitutor.camera.ExifImageTransform
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * OCR 직전에 촬영 이미지를 보정합니다.
 *
 * - EXIF 방향 보정 후 장변 [TARGET_LONG_SIDE]px로 정규화
 * - 투영 프로파일로 기울기를 추정해 ±[MAX_SKEW_DEGREES]° 이내만 보정
 * - 히스토그램 기반 대비 스트레칭
 * - 약한 언샤프(5점 라플라시안)로 글자 경계 강조
 *
 * PaddleOCR SDK는 건드리지 않고, 입력 파일만 새 임시 파일로 만들어 넘깁니다.
 * 어떤 단계든 실패하면 null을 반환하며 호출자는 원본 파일을 그대로 사용합니다.
 */
object OcrImageEnhancer {
    private const val TAG = "OcrImageEnhancer"

    /** OCR에 넘길 이미지의 장변 상한. SDK detMaxSideLimit(4000)보다 충분히 작게 유지합니다. */
    private const val TARGET_LONG_SIDE = 2400

    /** 이보다 작은 이미지는 확대하지 않고 그대로 둡니다. */
    private const val MIN_DESKEW_SIZE = 400

    private const val MAX_SKEW_DEGREES = 12f
    private const val MIN_SKEW_DEGREES = 0.6f

    /** 기울기 추정용 축소 이미지 장변. */
    private const val SKEW_SAMPLE_SIZE = 720
    private const val MAX_SKEW_POINTS = 30000

    private const val MAX_CONTRAST_SCALE = 2.2f
    private const val MIN_CONTRAST_SCALE = 1.05f

    private const val SHARPEN_SIDE_WEIGHT = 350 // amount 0.35
    private const val SHARPEN_CENTER_WEIGHT = 1000 + 4 * SHARPEN_SIDE_WEIGHT

    /** IntArray 두 개를 동시에 만들므로 너무 큰 이미지는 샤프닝을 건너뜁니다. */
    private const val MAX_SHARPEN_PIXELS = 5_000_000L

    private const val JPEG_QUALITY = 92

    /**
     * 보정된 임시 파일을 반환합니다. 보정할 필요가 없거나 실패하면 null을 반환합니다.
     */
    fun enhance(source: File): File? {
        if (!source.exists() || source.length() == 0L) return null
        return runCatching {
            var bitmap = decodeOriented(source) ?: return null
            try {
                bitmap = runStep(bitmap) { deskew(it) }
                bitmap = runStep(bitmap) { applyContrast(it) }
                bitmap = runStep(bitmap) { sharpen(it) }

                val output = File(source.parentFile, "focus_enh_${System.currentTimeMillis()}.jpg")
                try {
                    FileOutputStream(output).use { stream ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                            throw IllegalStateException("보정 이미지를 저장하지 못했습니다.")
                        }
                        stream.flush()
                    }
                } catch (error: Exception) {
                    output.delete()
                    throw error
                }
                output
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }.getOrNull()
    }

    /**
     * 보정 단계가 실패해도 원본 비트맵으로 계속 진행합니다.
     * 각 단계는 성공했을 때만 입력 비트맵을 recycle합니다.
     */
    private inline fun runStep(bitmap: Bitmap, step: (Bitmap) -> Bitmap): Bitmap {
        return runCatching { step(bitmap) }.getOrElse { error ->
            android.util.Log.w(TAG, "OCR 전처리 단계를 건너뜁니다: ${error.message}")
            bitmap
        }
    }

    private fun decodeOriented(source: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longest = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (longest / (sampleSize * 2) >= TARGET_LONG_SIDE) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null

        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val oriented = try {
            ExifImageTransform.fromOrientation(orientation).apply(decoded)
        } catch (error: Exception) {
            decoded.recycle()
            throw error
        }
        if (oriented !== decoded) decoded.recycle()

        val currentLongest = max(oriented.width, oriented.height)
        if (currentLongest <= TARGET_LONG_SIDE) return oriented

        val ratio = TARGET_LONG_SIDE.toFloat() / currentLongest
        val width = (oriented.width * ratio).toInt().coerceAtLeast(1)
        val height = (oriented.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(oriented, width, height, true)
        if (scaled !== oriented) oriented.recycle()
        return scaled
    }

    /**
     * 추정한 기울기만큼 회전합니다. 회전 후 잘리지 않도록 캔버스를 넓히고 흰색으로 채웁니다.
     */
    private fun deskew(bitmap: Bitmap): Bitmap {
        val angle = estimateSkewAngle(bitmap)
        if (abs(angle) < MIN_SKEW_DEGREES) return bitmap
        val rotationDegrees = -angle
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cosValue = abs(cos(radians)).toFloat()
        val sinValue = abs(sin(radians)).toFloat()
        val newWidth = (bitmap.width * cosValue + bitmap.height * sinValue).roundToInt()
        val newHeight = (bitmap.width * sinValue + bitmap.height * cosValue).roundToInt()
        if (newWidth <= 0 || newHeight <= 0) return bitmap

        val output = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val matrix = Matrix().apply {
            postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            postRotate(rotationDegrees)
            postTranslate(newWidth / 2f, newHeight / 2f)
        }
        canvas.drawBitmap(
            bitmap,
            matrix,
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        bitmap.recycle()
        return output
    }

    /**
     * 축소 그레이스케일 이미지의 검은 픽셀을 여러 각도로 투영해 가장 뭉치는 각도를 찾습니다.
     * 텍스트 줄이 수평일 때 투영 분산이 최대가 되는 성질을 이용합니다.
     */
    private fun estimateSkewAngle(bitmap: Bitmap): Float {
        val longest = max(bitmap.width, bitmap.height)
        if (longest < MIN_DESKEW_SIZE) return 0f

        val ratio = (SKEW_SAMPLE_SIZE.toFloat() / longest).coerceAtMost(1f)
        val sampleWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val sampleHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        if (sampleWidth < 32 || sampleHeight < 32) return 0f

        val sample = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, false)
        val pixels = IntArray(sampleWidth * sampleHeight)
        sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
        if (sample !== bitmap) sample.recycle()

        val luminance = IntArray(pixels.size)
        val histogram = IntArray(256)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val luma = (((pixel shr 16) and 0xFF) * 77 +
                ((pixel shr 8) and 0xFF) * 150 +
                (pixel and 0xFF) * 29) shr 8
            luminance[index] = luma
            histogram[luma]++
        }
        val threshold = otsuThreshold(histogram)

        val stride = max(1, pixels.size / (MAX_SKEW_POINTS * 4))
        val xs = ArrayList<Int>(MAX_SKEW_POINTS)
        val ys = ArrayList<Int>(MAX_SKEW_POINTS)
        var y = 0
        while (y < sampleHeight && xs.size < MAX_SKEW_POINTS) {
            val rowStart = y * sampleWidth
            var x = 0
            while (x < sampleWidth && xs.size < MAX_SKEW_POINTS) {
                if (luminance[rowStart + x] < threshold) {
                    xs.add(x)
                    ys.add(y)
                }
                x += stride
            }
            y += stride
        }
        if (xs.size < 50) return 0f

        val xArray = xs.toIntArray()
        val yArray = ys.toIntArray()

        var bestAngle = 0f
        var bestScore = Double.NEGATIVE_INFINITY
        var angle = -MAX_SKEW_DEGREES
        while (angle <= MAX_SKEW_DEGREES + 0.001f) {
            val score = projectionScore(xArray, yArray, sampleWidth, sampleHeight, angle)
            if (score > bestScore) {
                bestScore = score
                bestAngle = angle
            }
            angle += 1f
        }

        var refinedAngle = bestAngle
        var refinedScore = bestScore
        var fine = bestAngle - 1f
        while (fine <= bestAngle + 1.001f) {
            if (fine >= -MAX_SKEW_DEGREES && fine <= MAX_SKEW_DEGREES) {
                val score = projectionScore(xArray, yArray, sampleWidth, sampleHeight, fine)
                if (score > refinedScore) {
                    refinedScore = score
                    refinedAngle = fine
                }
            }
            fine += 0.25f
        }

        // 탐색 경계에 걸린 값은 신뢰할 수 없으므로 보정하지 않습니다.
        if (abs(refinedAngle) >= MAX_SKEW_DEGREES - 0.01f) return 0f
        return refinedAngle
    }

    private fun projectionScore(
        xs: IntArray,
        ys: IntArray,
        width: Int,
        height: Int,
        angleDegrees: Float
    ): Double {
        val tangent = tan(Math.toRadians(angleDegrees.toDouble()))
        val shiftLimit = (max(width, height) * 0.25).toInt() + 2
        val counts = IntArray(height + 2 * shiftLimit)
        val offset = shiftLimit
        for (index in xs.indices) {
            val shifted = ys[index] - (xs[index] * tangent).toInt()
            val position = shifted + offset
            if (position in counts.indices) counts[position]++
        }
        var score = 0.0
        for (count in counts) {
            score += count.toDouble() * count
        }
        return score
    }

    private fun otsuThreshold(histogram: IntArray): Int {
        val total = histogram.sum().toLong()
        if (total == 0L) return 128

        var sum = 0.0
        for (value in 0..255) sum += value.toDouble() * histogram[value]

        var sumBackground = 0.0
        var weightBackground = 0L
        var bestVariance = 0.0
        var threshold = 128
        for (value in 0..255) {
            weightBackground += histogram[value]
            if (weightBackground == 0L) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0L) break

            sumBackground += value.toDouble() * histogram[value]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sum - sumBackground) / weightForeground
            val variance = weightBackground.toDouble() * weightForeground *
                (meanBackground - meanForeground) * (meanBackground - meanForeground)
            if (variance > bestVariance) {
                bestVariance = variance
                threshold = value
            }
        }
        return threshold
    }

    /**
     * 2~98% 구간을 0~255로 늘려 대비를 올립니다. 이미 대비가 충분하면 그대로 둡니다.
     */
    private fun applyContrast(bitmap: Bitmap): Bitmap {
        val (lowPercentile, highPercentile) = luminancePercentiles(bitmap)
        val range = (highPercentile - lowPercentile).coerceAtLeast(1)
        val scale = (255f / range).coerceIn(1f, MAX_CONTRAST_SCALE)
        if (scale < MIN_CONTRAST_SCALE) return bitmap

        val translate = -lowPercentile * scale
        // setScale 후 postTranslate 대신 4x5 행렬을 직접 만들어 대비 스트레칭을 적용합니다.
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(
            bitmap,
            0f,
            0f,
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
        )
        bitmap.recycle()
        return output
    }

    private fun luminancePercentiles(bitmap: Bitmap): Pair<Int, Int> {
        val histogram = IntArray(256)
        val step = max(1, minOf(bitmap.width, bitmap.height) / 400)
        val rowBuffer = IntArray(bitmap.width)
        var y = 0
        while (y < bitmap.height) {
            bitmap.getPixels(rowBuffer, 0, bitmap.width, 0, y, bitmap.width, 1)
            var x = 0
            while (x < bitmap.width) {
                val pixel = rowBuffer[x]
                val luma = (((pixel shr 16) and 0xFF) * 77 +
                    ((pixel shr 8) and 0xFF) * 150 +
                    (pixel and 0xFF) * 29) shr 8
                histogram[luma]++
                x += step
            }
            y += step
        }

        val total = histogram.sum()
        if (total == 0) return 0 to 255
        val lowTarget = total * 2 / 100
        val highTarget = total * 98 / 100

        var accumulated = 0
        var lowValue = 0
        var highValue = 255
        for (value in 0..255) {
            accumulated += histogram[value]
            if (accumulated >= lowTarget) {
                lowValue = value
                break
            }
        }
        accumulated = 0
        for (value in 0..255) {
            accumulated += histogram[value]
            if (accumulated >= highTarget) {
                highValue = value
                break
            }
        }
        if (highValue - lowValue < 8) return lowValue to (lowValue + 8).coerceAtMost(255)
        return lowValue to highValue
    }

    /**
     * 5점 라플라시안 커널로 글자 경계를 살짝 강조합니다. 과도한 노이즈 증폭을 막기 위해
     * 강도는 0.35로 제한합니다.
     */
    private fun sharpen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return bitmap
        if (width.toLong() * height > MAX_SHARPEN_PIXELS) return bitmap

        val source = IntArray(width * height)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)
        val target = IntArray(source.size)

        for (y in 0 until height) {
            val rowStart = y * width
            val upRow = if (y > 0) rowStart - width else rowStart
            val downRow = if (y + 1 < height) rowStart + width else rowStart
            for (x in 0 until width) {
                val index = rowStart + x
                val upIndex = upRow + x
                val downIndex = downRow + x
                val leftIndex = if (x > 0) index - 1 else index
                val rightIndex = if (x + 1 < width) index + 1 else index

                val center = source[index]
                val up = source[upIndex]
                val down = source[downIndex]
                val left = source[leftIndex]
                val right = source[rightIndex]

                val red = sharpenChannel(
                    (center shr 16) and 0xFF,
                    (up shr 16) and 0xFF,
                    (down shr 16) and 0xFF,
                    (left shr 16) and 0xFF,
                    (right shr 16) and 0xFF
                )
                val green = sharpenChannel(
                    (center shr 8) and 0xFF,
                    (up shr 8) and 0xFF,
                    (down shr 8) and 0xFF,
                    (left shr 8) and 0xFF,
                    (right shr 8) and 0xFF
                )
                val blue = sharpenChannel(
                    center and 0xFF,
                    up and 0xFF,
                    down and 0xFF,
                    left and 0xFF,
                    right and 0xFF
                )
                target[index] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }

        val output = Bitmap.createBitmap(target, width, height, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        return output
    }

    private fun sharpenChannel(
        center: Int,
        up: Int,
        down: Int,
        left: Int,
        right: Int
    ): Int {
        val value = (SHARPEN_CENTER_WEIGHT * center -
            SHARPEN_SIDE_WEIGHT * (up + down + left + right)) / 1000
        return value.coerceIn(0, 255)
    }
}
