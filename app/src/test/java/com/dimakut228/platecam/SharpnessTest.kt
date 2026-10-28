package com.dimakut228.platecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharpnessTest {

    @Test
    fun flatImageHasNoVariance() {
        val gray = FloatArray(32 * 32) { 100f }
        assertEquals(0f, LaplacianVariance.of(gray, 32, 32), 0.001f)
    }

    @Test
    fun checkerboardIsSharperThanSmoothGradient() {
        val size = 32
        // Резкий кадр: максимальный контраст соседних пикселей.
        val checker = FloatArray(size * size) { i ->
            if (((i / size) + (i % size)) % 2 == 0) 0f else 255f
        }
        // Размытый кадр: линейный градиент, вторая производная равна нулю.
        val gradient = FloatArray(size * size) { i -> (i % size) * (255f / (size - 1)) }
        val sharp = LaplacianVariance.of(checker, size, size)
        val blurry = LaplacianVariance.of(gradient, size, size)
        assertTrue("checkerboard=$sharp gradient=$blurry", sharp > blurry * 10f)
    }

    @Test
    fun tinyImageHasNoVariance() {
        assertEquals(0f, LaplacianVariance.of(FloatArray(4), 2, 2), 0.001f)
    }
}
