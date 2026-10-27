package com.dimakut228.platecam

import org.junit.Assert.assertEquals
import org.junit.Test

class YoloDecodeTest {

    @Test
    fun decodesChannelsFirstOutput() {
        val output = FloatArray(10)
        output[0 * 2 + 0] = 320f
        output[1 * 2 + 0] = 320f
        output[2 * 2 + 0] = 200f
        output[3 * 2 + 0] = 40f
        output[4 * 2 + 0] = 0.9f

        val detections = YoloDecode.decode(output, longArrayOf(1, 5, 2), 0.5f, 0.45f, 640, true)
        assertEquals(1, detections.size)
        assertEquals(220f, detections[0].left, 0.01f)
        assertEquals(300f, detections[0].top, 0.01f)
        assertEquals(420f, detections[0].right, 0.01f)
        assertEquals(340f, detections[0].bottom, 0.01f)
    }

    @Test
    fun decodesAnchorsFirstOutput() {
        val output = FloatArray(10)
        output[0 * 5 + 0] = 320f
        output[0 * 5 + 1] = 320f
        output[0 * 5 + 2] = 200f
        output[0 * 5 + 3] = 40f
        output[0 * 5 + 4] = 0.9f

        val detections = YoloDecode.decode(output, longArrayOf(1, 2, 5), 0.5f, 0.45f, 640, false)
        assertEquals(1, detections.size)
        assertEquals(220f, detections[0].left, 0.01f)
    }
}
