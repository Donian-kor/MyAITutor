package com.donian.myaitutor.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * 촬영 이미지를 모델 입력에 적합한 형태로 정규화합니다.
 * - EXIF orientation에 따라 회전/반전
 * - 장변 2400px 기준 다운스케일 (PaddleOCR detMaxSideLimit 4000 이내 유지)
 * - JPEG 92%로 저장
 *
 * 실패 시 null을 반환하며, 호출자는 원본 파일을 그대로 사용할 수 있습니다.
 */
object ImageNormalizer {
    private const val MAX_SIDE_PX = 2400
    private const val JPEG_QUALITY = 92

    fun normalize(source: File): File? {
        if (!source.exists() || source.length() == 0L) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val orientation = ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            var sample = 1
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (sample * 2 <= maxSide / MAX_SIDE_PX) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeFile(source.absolutePath, opts) ?: return null

            val transformed = ExifImageTransform.fromOrientation(orientation).apply(decoded)
            if (transformed !== decoded) decoded.recycle()

            val finalBitmap = if (maxOf(transformed.width, transformed.height) > MAX_SIDE_PX) {
                val ratio = MAX_SIDE_PX.toFloat() / maxOf(transformed.width, transformed.height)
                val w = (transformed.width * ratio).toInt().coerceAtLeast(1)
                val h = (transformed.height * ratio).toInt().coerceAtLeast(1)
                transformed.scale(w, h, true).also {
                    if (it !== transformed) transformed.recycle()
                }
            } else {
                transformed
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
