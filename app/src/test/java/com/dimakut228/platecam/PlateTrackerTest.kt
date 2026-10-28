package com.dimakut228.platecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateTrackerTest {

    private fun detection() = Detection(10f, 10f, 150f, 50f, 0, 0.9f)

    private fun read(canonical: String, confidence: Float = 0.9f) = PlateRead(
        canonical = canonical,
        display = canonical,
        country = Country.RU,
        kind = "легковой",
        confidence = confidence,
        raw = canonical
    )

    @Test
    fun confirmsAfterTwoReads() {
        val tracker = PlateTracker(confirmReads = 2)
        val first = tracker.update(detection(), read("A123BC77", confidence = 0.85f))
        assertFalse(first.confirmed)
        val second = tracker.update(detection(), read("A123BC77", confidence = 0.85f))
        assertTrue(second.confirmed)
        assertEquals(2, second.votes)
    }

    @Test
    fun fastConfirmsHighConfidenceRead() {
        val tracker = PlateTracker(confirmReads = 2)
        val first = tracker.update(detection(), read("A123BC77", confidence = 0.95f))
        assertTrue(first.confirmed)
        assertEquals(2, first.votes)
    }

    @Test
    fun lowConfidenceReadDoesNotFastConfirm() {
        val tracker = PlateTracker(confirmReads = 2)
        val first = tracker.update(detection(), read("A123BC77", confidence = 0.7f))
        assertFalse(first.confirmed)
        assertEquals(1, first.votes)
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
