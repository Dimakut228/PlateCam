package com.dimakut228.platecam

class PlateTracker(
    private val confirmReads: Int = 2,
    private val fastConfirmConfidence: Float = 0.9f,
    private val iouThreshold: Float = 0.2f,
    private val maxMissed: Int = 6,
    private val keepDisplayMs: Long = 2500L,
    private val predictMaxAgeMs: Long = 600L,
    private val clock: () -> Long = System::currentTimeMillis
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
        var lastSeen: Long = 0L,
        val reads: ArrayDeque<PlateRead> = ArrayDeque(),
        var vx: Float = 0f,
        var vy: Float = 0f
    )

    private var track: Track? = null
    private var lastDisplay: Tracked? = null
    private var lastDisplayAt = 0L

    fun update(detection: Detection, read: PlateRead?): Tracked {
        val now = clock()
        var current = track
        if (current == null || !samePlate(current, detection, now)) {
            current = Track(detection, lastSeen = now)
            track = current
        } else {
            // Скорость центра номера: в машине от неё зависит предсказание
            // позиции на следующем кадре, когда детектор номер «теряет».
            val dtSeconds = (now - current.lastSeen).coerceAtLeast(1L) / 1000f
            val instVx = (detection.centerX - current.detection.centerX) / dtSeconds
            val instVy = (detection.centerY - current.detection.centerY) / dtSeconds
            current.vx = current.vx * SMOOTHING + instVx * (1f - SMOOTHING)
            current.vy = current.vy * SMOOTHING + instVy * (1f - SMOOTHING)
            current.detection = detection
            current.missed = 0
            current.lastSeen = now
        }
        if (read != null) {
            current.reads.addLast(read)
            while (current.reads.size > 8) current.reads.removeFirst()
        }
        val consensus = consensus(current.reads)
        var votes = consensus?.second ?: 0
        // Очень уверенное чтение не ждёт второго кадра.
        if (votes < confirmReads && confirmReads > 1 &&
            current.reads.any { it.confidence >= fastConfirmConfidence }
        ) {
            votes = confirmReads
        }
        val result = Tracked(detection, consensus?.first, votes >= confirmReads, votes)
        lastDisplay = result
        lastDisplayAt = now
        return result
    }

    /**
     * Кадр без детекции. [read] — чтение по предсказанной области (тряска
     * рвёт детекцию, но номер остаётся в кадре): голос учитывается,
     * пока трек жив.
     */
    fun miss(read: PlateRead? = null): Tracked? {
        val now = clock()
        val current = track
        if (current == null) {
            return lastDisplay?.takeIf { now - lastDisplayAt <= keepDisplayMs }
        }
        current.missed++
        // Промахи затухают скорость: без новых измерений предсказание плывёт.
        current.vx *= VELOCITY_DECAY
        current.vy *= VELOCITY_DECAY
        if (read != null) {
            current.reads.addLast(read)
            while (current.reads.size > 8) current.reads.removeFirst()
        }
        if (current.missed > maxMissed) {
            track = null
            return lastDisplay?.takeIf { now - lastDisplayAt <= keepDisplayMs }
        }
        val consensus = consensus(current.reads)
        val votes = consensus?.second ?: 0
        return Tracked(current.detection, consensus?.first, votes >= confirmReads, votes)
    }

    /**
     * Предсказанное положение номера с учётом его движения; null, если
     * трек протух и предсказание уже ни на что не опирается.
     */
    fun predict(now: Long = clock()): Detection? {
        val current = track ?: return null
        if (now - current.lastSeen > predictMaxAgeMs) return null
        return predictedDetection(current, now)
    }

    fun reset() {
        track = null
        lastDisplay = null
        lastDisplayAt = 0L
    }

    private fun samePlate(track: Track, detection: Detection, now: Long): Boolean {
        // Связываем не с прошлым боксом, а с ожидаемым: тряска и пропуск
        // размытых кадров срывают совпадение боксов между кадрами.
        val expected = predictedDetection(track, now)
        if (expected.iou(detection) >= iouThreshold) return true
        val limit = maxOf(expected.width, expected.height) * 0.8f
        return expected.distanceTo(detection) <= limit
    }

    private fun predictedDetection(track: Track, now: Long): Detection {
        val elapsedSeconds = (now - track.lastSeen).coerceAtLeast(0L) / 1000f
        return shift(track.detection, track.vx * elapsedSeconds, track.vy * elapsedSeconds)
    }

    private fun shift(d: Detection, dx: Float, dy: Float): Detection {
        // Сдвиг ограничиваем долями размера номера: разогнавшаяся тряска
        // не должна уводить область за пределы реального номера.
        val capX = d.width * MAX_SHIFT_FACTOR
        val capY = d.height * MAX_SHIFT_FACTOR
        val sx = dx.coerceIn(-capX, capX)
        val sy = dy.coerceIn(-capY, capY)
        if (sx == 0f && sy == 0f) return d
        return Detection(d.left + sx, d.top + sy, d.right + sx, d.bottom + sy, d.classIndex, d.score)
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

    private companion object {
        const val SMOOTHING = 0.5f
        const val VELOCITY_DECAY = 0.7f
        const val MAX_SHIFT_FACTOR = 1.5f
    }
}
