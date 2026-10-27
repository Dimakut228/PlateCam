package com.dimakut228.platecam

enum class PerformanceTier(val label: String) {
    LOW("эконом"),
    MID("средний"),
    HIGH("быстрый")
}

data class PerformanceProfile(
    val tier: PerformanceTier,
    val analysisWidth: Int,
    val analysisHeight: Int,
    val minFps: Int,
    val maxFps: Int,
    val threads: Int,
    val allowTiles: Boolean,
    val tileMaxCostMs: Long,
    val ocrWidth: Int,
    val ocrIntervalMs: Long,
    val parserVariants: Int
)

object PerformanceTuning {
    fun forDevice(
        cores: Int,
        lowRam: Boolean,
        memoryClassMb: Int,
        is64Bit: Boolean
    ): PerformanceProfile {
        val safeCores = cores.coerceAtLeast(1)
        val tier = when {
            lowRam || safeCores <= 2 || memoryClassMb <= 128 -> PerformanceTier.LOW
            safeCores >= 6 && memoryClassMb >= 384 -> PerformanceTier.HIGH
            else -> PerformanceTier.MID
        }
        val threads = when (tier) {
            PerformanceTier.LOW -> safeCores.coerceIn(1, 2)
            PerformanceTier.MID -> safeCores.coerceIn(2, 3)
            PerformanceTier.HIGH -> safeCores.coerceIn(3, 4)
        }.let { if (is64Bit) it else it.coerceAtMost(2) }

        return when (tier) {
            PerformanceTier.LOW -> PerformanceProfile(
                tier = tier,
                analysisWidth = 960,
                analysisHeight = 540,
                minFps = 1,
                maxFps = 4,
                threads = threads,
                allowTiles = false,
                tileMaxCostMs = 0L,
                ocrWidth = 256,
                ocrIntervalMs = 500L,
                parserVariants = 180
            )
            PerformanceTier.MID -> PerformanceProfile(
                tier = tier,
                analysisWidth = 1280,
                analysisHeight = 720,
                minFps = 2,
                maxFps = 6,
                threads = threads,
                allowTiles = true,
                tileMaxCostMs = 100L,
                ocrWidth = 320,
                ocrIntervalMs = 350L,
                parserVariants = 320
            )
            PerformanceTier.HIGH -> PerformanceProfile(
                tier = tier,
                analysisWidth = 1280,
                analysisHeight = 720,
                minFps = 3,
                maxFps = 8,
                threads = threads,
                allowTiles = true,
                tileMaxCostMs = 180L,
                ocrWidth = 320,
                ocrIntervalMs = 300L,
                parserVariants = 700
            )
        }
    }
}
