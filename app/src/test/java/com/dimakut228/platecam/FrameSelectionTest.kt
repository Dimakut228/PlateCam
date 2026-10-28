package com.dimakut228.platecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameSelectionTest {

    @Test
    fun acceptsFirstFrameForcibly() {
        val selection = FrameSelection()
        assertTrue(selection.accept(1f, 0L))
    }

    @Test
    fun rejectsBlurryFrameWhenSharpSeen() {
        val selection = FrameSelection()
        assertTrue(selection.accept(200f, 0L))
        // 40 < 200 * 0.55 — кадр размыт относительно недавнего пика.
        assertFalse(selection.accept(40f, 50L))
        assertEquals(1, selection.rejected)
    }

    @Test
    fun acceptsFrameCloseToPeak() {
        val selection = FrameSelection()
        selection.accept(200f, 0L)
        assertTrue(selection.accept(150f, 50L))
    }

    @Test
    fun forcedFrameAfterTimeout() {
        val selection = FrameSelection(forceAfterMs = 300L)
        selection.accept(200f, 0L)
        assertFalse(selection.accept(10f, 100L))
        // Кадры давно не принимались — берём любой, чтобы не застрять.
        assertTrue(selection.accept(10f, 400L))
    }

    @Test
    fun peakDecaysSoModerateFramePasses() {
        val selection = FrameSelection(forceAfterMs = 60_000L)
        assertTrue(selection.accept(1000f, 0L))
        // Резкость упала — кадр отбракован.
        assertFalse(selection.accept(400f, 100L))
        // Пик спал за ~3 с — умеренно резкий кадр снова проходит.
        assertTrue(selection.accept(400f, 3000L))
    }

    @Test
    fun resetClearsState() {
        val selection = FrameSelection()
        selection.accept(200f, 0L)
        selection.accept(10f, 50L)
        selection.reset()
        assertEquals(0, selection.rejected)
        // После сброса первый кадр принимается принудительно.
        assertTrue(selection.accept(1f, 1000L))
    }
}
