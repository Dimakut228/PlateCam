package com.dimakut228.platecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateTrackerTest {

    private fun detection() = Detection(10f, 10f, 150f, 50f, 0, 0.9f)

    private fun read(canonical: String) = PlateRead(
        canonical = canonical,
        display = canonical,
        country = Country.RU,
        kind = "легковой",
        confidence = 0.9f,
        raw = canonical
    )

    @Test
    fun confirmsAfterTwoReads() {
        val tracker = PlateTracker(confirmReads = 2)
        val first = tracker.update(detection(), read("A123BC77"))
        assertFalse(first.confirmed)
        val second = tracker.update(detection(), read("A123BC77"))
        assertTrue(second.confirmed)
        assertEquals(2, second.votes)
    }

    @Test
    fun keepsBestReading() {
        val tracker = PlateTracker(confirmReads = 2)
        tracker.update(detection(), read("A123BC77"))
        val result = tracker.update(detection(), read("A123BC77"))
        assertEquals("A123BC77", result.read?.canonical)
    }

    @Test
    fun resetClearsState() {
        val tracker = PlateTracker(confirmReads = 1)
        tracker.update(detection(), read("A123BC77"))
        tracker.reset()
        val result = tracker.miss()
        assertEquals(null, result)
    }
}
