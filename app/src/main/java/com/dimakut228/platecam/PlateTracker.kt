package com.dimakut228.platecam

class PlateTracker(
    private val confirmReads: Int = 2,
    private val iouThreshold: Float = 0.2f,
    private val maxMissed: Int = 6,
    private val keepDisplayMs: Long = 2500L
) {

    data class Tracked(
        val detection: Detection?,
        val read: PlateRead?,
        val confirmed: Boolean,
        val votes: Int
    )

    private class Track(
        var detection: Detection,
        var missed: Int = 0,
        var lastSeen: Long = System.currentTimeMillis(),
        val reads: ArrayDeque<PlateRead> = ArrayDeque()
    )

    private var track: Track? = null
    private var lastDisplay: Tracked? = null
    private var lastDisplayAt = 0L

    fun update(detection: Detection, read: PlateRead?): Tracked {
        val now = System.currentTimeMillis()
        var current = track
        if (current == null || !samePlate(current.detection, detection)) {
            current = Track(detection, lastSeen = now)
            track = current
        } else {
            current.detection = detection
            current.missed = 0
            current.lastSeen = now
        }
        if (read != null) {
            current.reads.addLast(read)
            while (current.reads.size > 8) current.reads.removeFirst()
        }
        val consensus = consensus(current.reads)
        val votes = consensus?.second ?: 0
        val result = Tracked(detection, consensus?.first, votes >= confirmReads, votes)
        lastDisplay = result
        lastDisplayAt = now
        return result
    }

    fun miss(): Tracked? {
        val now = System.currentTimeMillis()
        val current = track
        if (current == null) {
            return lastDisplay?.takeIf { now - lastDisplayAt <= keepDisplayMs }
        }
        current.missed++
        if (current.missed > maxMissed) {
            track = null
            return lastDisplay?.takeIf { now - lastDisplayAt <= keepDisplayMs }
        }
        val consensus = consensus(current.reads)
        val votes = consensus?.second ?: 0
        return Tracked(current.detection, consensus?.first, votes >= confirmReads, votes)
    }

    fun reset() {
        track = null
        lastDisplay = null
        lastDisplayAt = 0L
    }

    private fun samePlate(a: Detection, b: Detection): Boolean {
        if (a.iou(b) >= iouThreshold) return true
        val limit = maxOf(a.width, a.height) * 0.8f
        return a.distanceTo(b) <= limit
    }

    private fun consensus(reads: ArrayDeque<PlateRead>): Pair<PlateRead, Int>? {
        if (reads.isEmpty()) return null
        val groups = ArrayList<MutableList<PlateRead>>()
        for (read in reads) {
            val group = groups.firstOrNull { editDistance(it.first().canonical, read.canonical) <= 1 }
            if (group != null) group.add(read) else groups.add(mutableListOf(read))
        }
        var bestGroup: MutableList<PlateRead>? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (group in groups) {
            val counts = group.groupingBy { it.canonical }.eachCount()
            val top = counts.values.maxOrNull() ?: 0
            val average = group.sumOf { it.confidence.toDouble() }.toFloat() / group.size
            val score = top * 2f + average * 1.5f
            if (score > bestScore) {
                bestScore = score
                bestGroup = group
            }
        }
        val group = bestGroup ?: return null
        val counts = group.groupingBy { it.canonical }.eachCount()
        val canonical = counts.maxByOrNull { it.value }?.key ?: group.first().canonical
        val representative = group.filter { it.canonical == canonical }.maxByOrNull { it.confidence }
            ?: group.maxByOrNull { it.confidence }
            ?: return null
        return representative to group.size
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
