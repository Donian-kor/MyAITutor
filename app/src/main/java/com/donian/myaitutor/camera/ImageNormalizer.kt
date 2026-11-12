package com.donian.myaitutor.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * 촬영 이미지를 모델 입력에 적합한 형태로 정규화합니다.
 * - EXIF orientation에 따라 회전
 * - 장변 1600px 기준 다운스케일
 * - JPEG 85%로 저장
 *
 * 실패 시 null을 반환하며, 호출자는 원본 파일을 그대로 사용할 수 있습니다.
 */
object ImageNormalizer {
    private const val MAX_SIDE_PX = 1600
    private const val JPEG_QUALITY = 85

    fun normalize(source: File): File? {
        if (!source.exists() || source.length() == 0L) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val orientation = ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            var sample = 1
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (sample * 2 <= maxSide / MAX_SIDE_PX) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeFile(source.absolutePath, opts) ?: return null

            val rotated = if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it != decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val finalBitmap = if (maxOf(rotated.width, rotated.height) > MAX_SIDE_PX) {
                val ratio = MAX_SIDE_PX.toFloat() / maxOf(rotated.width, rotated.height)
                val w = (rotated.width * ratio).toInt().coerceAtLeast(1)
                val h = (rotated.height * ratio).toInt().coerceAtLeast(1)
                rotated.scale(w, h, true).also {
                    if (it != rotated) rotated.recycle()
                }
            } else {
                rotated
            }

            val outFile = File(source.parentFile, "focus_norm_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { stream ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                stream.flush()
            }
            finalBitmap.recycle()
            outFile
        }.getOrNull()
    }
}
