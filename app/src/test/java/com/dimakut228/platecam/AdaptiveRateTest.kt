package com.dimakut228.platecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRateTest {

    @Test
    fun normalIntervalFollowsFps() {
        val rate = AdaptiveRate(minFps = 2, maxFps = 8, startFps = 4)
        assertEquals(250L, rate.intervalMs())
    }

    @Test
    fun boostRemovesInterval() {
        val rate = AdaptiveRate(minFps = 2, maxFps = 8, startFps = 2)
        rate.boost = true
        assertEquals(0L, rate.intervalMs())
    }

    @Test
    fun boostPreventsSlowdown() {
        val rate = AdaptiveRate(minFps = 2, maxFps = 8, startFps = 4)
        rate.boost = true
        // Три тяжёлых кадра подряд: fps не должен просесть во время поиска.
        repeat(4) { rate.report(1500L) }
        assertTrue(rate.fps >= 4)
        assertFalse(rate.intervalMs() > 0L)
    }

    @Test
    fun slowdownAfterSearch() {
        val rate = AdaptiveRate(minFps = 2, maxFps = 8, startFps = 4)
        repeat(6) { rate.report(1500L) }
        assertEquals(2, rate.fps)
    }
}
