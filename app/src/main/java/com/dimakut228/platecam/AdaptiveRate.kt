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

    /**
     * Режим поиска номера: кадры обрабатываются подряд без интервальных пауз,
     * чтобы поймать быстро движущийся номер. Ограничением остаётся только
     * фактическая стоимость кадра, т. к. камера работает в KEEP_ONLY_LATEST.
     */
    @Volatile var boost: Boolean = false

    fun intervalMs(): Long = if (boost) 0L else 1000L / fps.coerceAtLeast(1)

    fun reset(targetFps: Int) {
        fps = targetFps.coerceIn(minFps, maxFps)
        smoothCostMs = 0f
        samples = 0
    }

    fun report(costMs: Long): Int {
        val cost = costMs.coerceAtLeast(0L).toFloat()
        smoothCostMs = if (samples == 0) cost else smoothCostMs * 0.7f + cost * 0.3f
        samples++
        if (samples < 3) return fps
        samples = 0
        val budget = 1000f / fps.coerceAtLeast(1)
        fps = when {
            smoothCostMs > budget * 1.35f -> {
                // Пока идёт поиск, fps и так не ограничивает обработку —
                // не замедляемся дальше минимума.
                if (boost) fps else (fps - 1).coerceAtLeast(minFps)
            }
            smoothCostMs < budget * 0.55f -> (fps + 1).coerceAtMost(maxFps)
            else -> fps
        }
        return fps
    }
}
