package com.dimakut228.platecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTuningTest {

    @Test
    fun lowEndUsesConservativeSettings() {
        val tuning = PerformanceTuning.forDevice(2, lowRam = false, memoryClassMb = 128, is64Bit = false)
        assertEquals(PerformanceTier.LOW, tuning.tier)
        assertTrue(tuning.analysisWidth <= 960)
        assertTrue(tuning.threads <= 2)
        assertFalse(tuning.allowTiles)
    }

    @Test
    fun lowRamForcesEconomyMode() {
        val tuning = PerformanceTuning.forDevice(8, lowRam = true, memoryClassMb = 512, is64Bit = true)
        assertEquals(PerformanceTier.LOW, tuning.tier)
        assertEquals(2, tuning.threads)
    }

    @Test
    fun fastDeviceUsesMoreThreadsAndFrames() {
        val tuning = PerformanceTuning.forDevice(8, lowRam = false, memoryClassMb = 512, is64Bit = true)
        assertEquals(PerformanceTier.HIGH, tuning.tier)
        assertEquals(4, tuning.threads)
        assertEquals(8, tuning.maxFps)
        assertTrue(tuning.allowTiles)
    }

    @Test
    fun thirtyTwoBitDeviceNeverUsesMoreThanTwoThreads() {
        val tuning = PerformanceTuning.forDevice(8, lowRam = false, memoryClassMb = 512, is64Bit = false)
        assertEquals(2, tuning.threads)
    }
}
