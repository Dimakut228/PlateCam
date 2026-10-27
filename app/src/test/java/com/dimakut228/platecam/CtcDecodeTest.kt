package com.dimakut228.platecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CtcDecodeTest {

    @Test
    fun decodesSequenceAndSkipsBlank() {
        val keys = (('0'..'9').map { it.toString() } + listOf("A", "B"))
        val classes = keys.size + 1
        val timesteps = 3
        val logits = FloatArray(timesteps * classes)
        logits[0 * classes + 11] = 10f
        logits[1 * classes + 0] = 10f
        logits[2 * classes + 12] = 10f
        val allowed = BooleanArray(classes) { true }
        val result = CtcDecode.decode(logits, timesteps, classes, keys, allowed)
        assertEquals("AB", result.text)
        assertTrue(result.confidence > 0.9f)
    }

    @Test
    fun readsProbabilityOutputWithoutSoftmax() {
        val keys = (('0'..'9').map { it.toString() } + listOf("A", "B"))
        val classes = keys.size + 1
        val timesteps = 3
        val probabilities = FloatArray(timesteps * classes)
        probabilities[0 * classes + 11] = 1f
        probabilities[1 * classes + 0] = 1f
        probabilities[2 * classes + 12] = 1f
        val allowed = BooleanArray(classes) { true }
        val result = CtcDecode.decode(probabilities, timesteps, classes, keys, allowed)
        assertEquals("AB", result.text)
        assertTrue(result.confidence > 0.9f)
    }
}
