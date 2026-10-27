package com.dimakut228.platecam

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrSizingTest {

    @Test
    fun typicalPlateKeepsAspectRatio() {
        assertEquals(192, OcrSizing.widthFor(400, 100, 320, 48))
    }

    @Test
    fun widePlateIsCapped() {
        assertEquals(320, OcrSizing.widthFor(1000, 100, 320, 48))
    }

    @Test
    fun narrowCropUsesMinimum() {
        assertEquals(160, OcrSizing.widthFor(100, 100, 320, 48))
    }

    @Test
    fun widthIsRoundedToModelStride() {
        assertEquals(200, OcrSizing.widthFor(410, 100, 320, 48))
        assertEquals(224, OcrSizing.widthFor(465, 100, 320, 48))
    }

    @Test
    fun lowProfileCapIsRespected() {
        assertEquals(256, OcrSizing.widthFor(800, 100, 256, 48))
    }
}
