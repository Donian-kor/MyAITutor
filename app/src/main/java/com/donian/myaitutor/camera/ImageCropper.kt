package com.donian.myaitutor.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object ImageCropper {
    /**
     * 사용자가 그린 영역에 약간의 여백을 더해 글자가 잘리지 않게 합니다.
     * 여백은 이미지 짧은 변의 2.5%(6~48px)로 제한합니다.
     */
    private const val PADDING_RATIO = 0.025f
    private const val MIN_PADDING_PX = 6
    private const val MAX_PADDING_PX = 48

    fun crop(source: File, selection: ImageSelection): File {
        val decoded = BitmapFactory.decodeFile(source.absolutePath)
            ?: throw IllegalArgumentException("선택 영역 이미지를 읽지 못했습니다.")

        val oriented = try {
            val orientation = ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            ExifImageTransform.fromOrientation(orientation).apply(decoded)
        } catch (error: Exception) {
            decoded.recycle()
            throw error
        }
        if (oriented !== decoded) decoded.recycle()

        val output = File(source.parentFile, "focus_crop_${System.currentTimeMillis()}.png")
        var cropped: Bitmap? = null
        try {
            val padding = (minOf(oriented.width, oriented.height) * PADDING_RATIO)
                .toInt()
                .coerceIn(MIN_PADDING_PX, MAX_PADDING_PX)
            val left = ((selection.left * oriented.width).toInt() - padding)
                .coerceIn(0, oriented.width - 1)
            val top = ((selection.top * oriented.height).toInt() - padding)
                .coerceIn(0, oriented.height - 1)
            val right = ((selection.right * oriented.width).toInt() + padding)
                .coerceIn(left + 1, oriented.width)
            val bottom = ((selection.bottom * oriented.height).toInt() + padding)
                .coerceIn(top + 1, oriented.height)
            cropped = Bitmap.createBitmap(oriented, left, top, right - left, bottom - top)
            FileOutputStream(output).use { stream ->
                if (!cropped.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IllegalStateException("선택 영역 이미지를 저장하지 못했습니다.")
                }
                stream.flush()
            }
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            cropped?.let { if (it !== oriented) it.recycle() }
            oriented.recycle()
        }
        return output
    }
}
