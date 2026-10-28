package com.dimakut228.platecam

import kotlin.math.exp

/**
 * Отбор кадров при тряске и движении: пропускает размытые кадры и
 * обрабатывает самые резкие, не застревая в постоянно размытой сцене.
 *
 * Порог адаптивный — доля от максимальной резкости за последние секунды
 * (пик спадает экспоненциально, поэтому сцене легко «восстановиться»).
 * Если кадры давно не принимались, следующий кадр принимается принудительно:
 * иначе на постоянном смазе конвейер встал бы совсем.
 */
class FrameSelection(
    private val acceptRatio: Float = 0.55f,
    private val minSharpness: Float = 2f,
    private val forceAfterMs: Long = 350L,
    private val peakTimeConstantMs: Float = 1200f
) {

    private var peak = 0f
    private var hasPeak = false
    private var lastPeakAt = 0L
    private var lastAcceptedAt = 0L
    private var hasAccepted = false

    /** Число отброшенных размытых кадров (для статуса). */
    @Volatile
    var rejected: Int = 0
        private set

    fun reset() {
        peak = 0f
        hasPeak = false
        lastPeakAt = 0L
        lastAcceptedAt = 0L
        hasAccepted = false
        rejected = 0
    }

    /** true — кадр достаточно резкий, стоит запускать детектор. */
    @Synchronized
    fun accept(sharpness: Float, nowMs: Long): Boolean {
        peak = if (!hasPeak) {
            sharpness
        } else {
            val decay = exp(-(nowMs - lastPeakAt).coerceAtLeast(0L) / peakTimeConstantMs)
            maxOf(sharpness, (peak * decay).toFloat())
        }
        hasPeak = true
        lastPeakAt = nowMs
        val threshold = maxOf(minSharpness, peak * acceptRatio)
        val forced = !hasAccepted || nowMs - lastAcceptedAt >= forceAfterMs
        val accepted = forced || sharpness >= threshold
        if (accepted) {
            hasAccepted = true
            lastAcceptedAt = nowMs
        } else {
            rejected++
        }
        return accepted
    }
}
