package com.donian.myaitutor.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface

data class ExifImageTransform(
    val rotation: Float,
    val flipHorizontal: Boolean
) {
    companion object {
        fun fromOrientation(orientation: Int) = when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifImageTransform(0f, true)
            ExifInterface.ORIENTATION_ROTATE_180 -> ExifImageTransform(180f, false)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifImageTransform(180f, true)
            ExifInterface.ORIENTATION_TRANSPOSE -> ExifImageTransform(90f, true)
            ExifInterface.ORIENTATION_ROTATE_90 -> ExifImageTransform(90f, false)
            ExifInterface.ORIENTATION_TRANSVERSE -> ExifImageTransform(270f, true)
            ExifInterface.ORIENTATION_ROTATE_270 -> ExifImageTransform(270f, false)
            else -> ExifImageTransform(0f, false)
        }
    }

    fun apply(bitmap: Bitmap): Bitmap {
        if (rotation == 0f && !flipHorizontal) return bitmap
        val matrix = Matrix().apply {
            if (flipHorizontal) postScale(-1f, 1f)
            if (rotation != 0f) postRotate(rotation)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
