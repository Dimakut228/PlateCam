package com.dimakut228.platecam

object YoloDecode {

    fun decode(
        output: FloatArray,
        shape: LongArray,
        threshold: Float,
        iouThreshold: Float,
        imageSize: Int,
        channelsFirst: Boolean? = null
    ): List<Detection> {
        if (shape.size != 3 || output.isEmpty()) return emptyList()
        val d1 = shape[1].toInt()
        val d2 = shape[2].toInt()
        if (d1 <= 0 || d2 <= 0) return emptyList()
        val first = channelsFirst ?: when {
            d1 <= 16 && d2 > 16 -> true
            d2 <= 16 && d1 > 16 -> false
            else -> d1 <= d2
        }
        val channels = if (first) d1 else d2
        val anchors = if (first) d2 else d1
        val classCount = channels - 4
        if (classCount <= 0 || anchors <= 0 || output.size < channels * anchors) return emptyList()

        var maxCoord = 0f
        for (a in 0 until anchors) {
            for (c in 0 until 4) {
                val v = value(output, first, channels, anchors, a, c)
                if (v > maxCoord) maxCoord = v
            }
        }
        val coordScale = if (maxCoord <= 2f) imageSize.toFloat() else 1f

        val candidates = ArrayList<Detection>(16)
        for (a in 0 until anchors) {
            var bestScore = 0f
            var bestClass = -1
            for (c in 0 until classCount) {
                val s = value(output, first, channels, anchors, a, 4 + c)
                if (s > bestScore) {
                    bestScore = s
                    bestClass = c
                }
            }
            if (bestScore < threshold || bestClass < 0) continue
            val cx = value(output, first, channels, anchors, a, 0) * coordScale
            val cy = value(output, first, channels, anchors, a, 1) * coordScale
            val w = value(output, first, channels, anchors, a, 2) * coordScale
            val h = value(output, first, channels, anchors, a, 3) * coordScale
            val left = (cx - w / 2f).coerceIn(0f, imageSize.toFloat())
            val top = (cy - h / 2f).coerceIn(0f, imageSize.toFloat())
            val right = (cx + w / 2f).coerceIn(0f, imageSize.toFloat())
            val bottom = (cy + h / 2f).coerceIn(0f, imageSize.toFloat())
            if (right - left < 1f || bottom - top < 1f) continue
            candidates.add(Detection(left, top, right, bottom, bestClass, bestScore))
        }
        return nms(candidates, iouThreshold)
    }

    private fun value(
        output: FloatArray,
        channelsFirst: Boolean,
        channels: Int,
        anchors: Int,
        anchor: Int,
        channel: Int
    ): Float {
        val index = if (channelsFirst) channel * anchors + anchor else anchor * channels + channel
        return if (index in output.indices) output[index] else 0f
    }

    fun nms(candidates: List<Detection>, iouThreshold: Float): List<Detection> {
        val sorted = candidates.sortedByDescending { it.score }
        val kept = ArrayList<Detection>(sorted.size)
        val suppressed = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val current = sorted[i]
            kept.add(current)
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                if (sorted[j].classIndex == current.classIndex && current.iou(sorted[j]) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return kept
    }
}
