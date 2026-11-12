package com.donian.myaitutor.camera

data class ImageSelection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left in 0f..1f && top in 0f..1f)
        require(right in 0f..1f && bottom in 0f..1f)
        require(left < right && top < bottom)
    }

    companion object {
        fun fromPoints(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            width: Float,
            height: Float
        ): ImageSelection? {
            if (width <= 0f || height <= 0f) return null
            val left = minOf(startX, endX).coerceIn(0f, width)
            val right = maxOf(startX, endX).coerceIn(0f, width)
            val top = minOf(startY, endY).coerceIn(0f, height)
            val bottom = maxOf(startY, endY).coerceIn(0f, height)
            if (right - left < 2f || bottom - top < 2f) return null

            return ImageSelection(
                left = left / width,
                top = top / height,
                right = right / width,
                bottom = bottom / height
            )
        }
    }
}
