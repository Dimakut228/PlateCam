package com.dimakut228.platecam

import kotlin.math.exp

object CtcDecode {

    data class Result(val text: String, val confidence: Float)

    fun decode(
        logits: FloatArray,
        timesteps: Int,
        classes: Int,
        keys: List<String>,
        allowed: BooleanArray
    ): Result {
        if (timesteps <= 0 || classes <= 0) return Result("", 0f)
        val text = StringBuilder()
        var previous = -1
        var confidenceSum = 0f
        var confidenceCount = 0
        for (t in 0 until timesteps) {
            val offset = t * classes
            var best = -1
            var bestLogit = Float.NEGATIVE_INFINITY
            var sumAll = 0f
            var minValue = Float.POSITIVE_INFINITY
            for (c in 0 until classes) {
                val value = logits[offset + c]
                sumAll += value
                if (value < minValue) minValue = value
                if (c >= allowed.size || !allowed[c]) continue
                if (value > bestLogit) {
                    bestLogit = value
                    best = c
                }
            }
            if (best < 0) continue
            val probability = if (minValue >= 0f && sumAll in 0.95f..1.05f) {
                bestLogit.coerceIn(0f, 1f)
            } else {
                var expSum = 0f
                for (c in 0 until classes) {
                    if (c >= allowed.size || !allowed[c]) continue
                    expSum += exp((logits[offset + c] - bestLogit).toDouble()).toFloat()
                }
                if (expSum > 0f) 1f / expSum else 0f
            }
            if (best != 0 && best != previous) {
                val index = best - 1
                if (index in keys.indices) text.append(keys[index])
            }
            if (best != 0) {
                confidenceSum += probability
                confidenceCount++
            }
            previous = best
        }
        val confidence = if (confidenceCount > 0) confidenceSum / confidenceCount else 0f
        return Result(text.toString(), confidence.coerceIn(0f, 1f))
    }
}
