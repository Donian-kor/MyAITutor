package com.donian.myaitutor.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageSelectionTest {
    @Test
    fun `drag point order does not change the normalized selection`() {
        val selection = ImageSelection.fromPoints(
            startX = 80f,
            startY = 60f,
            endX = 20f,
            endY = 10f,
            width = 100f,
            height = 100f
        )

        assertEquals(ImageSelection(0.2f, 0.1f, 0.8f, 0.6f), selection)
    }

    @Test
    fun `selection is clamped to image bounds`() {
        val selection = ImageSelection.fromPoints(
            startX = -10f,
            startY = 10f,
            endX = 120f,
            endY = 90f,
            width = 100f,
            height = 100f
        )

        assertEquals(ImageSelection(0f, 0.1f, 1f, 0.9f), selection)
    }

    @Test
    fun `tiny or invalid image selection is ignored`() {
        assertNull(ImageSelection.fromPoints(10f, 10f, 11f, 40f, 100f, 100f))
        assertNull(ImageSelection.fromPoints(0f, 0f, 10f, 10f, 0f, 100f))
    }
}
