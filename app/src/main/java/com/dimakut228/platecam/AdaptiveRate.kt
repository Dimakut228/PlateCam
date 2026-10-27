package com.dimakut228.platecam

class AdaptiveRate(
    private val minFps: Int = 3,
    private val maxFps: Int = 8,
    startFps: Int = maxFps
) {
    var fps: Int = startFps.coerceIn(minFps, maxFps)
        private set

    private var smoothCostMs = 0f
    private var samples = 0

    fun intervalMs(): Long = 1000L / fps.coerceAtLeast(1)

    fun reset(targetFps: Int) {
        fps = targetFps.coerceIn(minFps, maxFps)
        smoothCostMs = 0f
        samples = 0
    }

    fun report(costMs: Long): Int {
        val cost = costMs.coerceAtLeast(0L).toFloat()
        smoothCostMs = if (samples == 0) cost else smoothCostMs * 0.7f + cost * 0.3f
        samples++
        if (samples < 6) return fps
        samples = 0
        val budget = 1000f / fps.coerceAtLeast(1)
        fps = when {
            smoothCostMs > budget * 1.35f -> (fps - 1).coerceAtLeast(minFps)
            smoothCostMs < budget * 0.55f -> (fps + 1).coerceAtMost(maxFps)
            else -> fps
        }
        return fps
    }
}
