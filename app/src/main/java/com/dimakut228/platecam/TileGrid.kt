package com.dimakut228.platecam

object TileGrid {

    data class Window(val left: Int, val top: Int, val width: Int, val height: Int)

    fun windows(width: Int, height: Int, count: Int, overlap: Float = 0.2f): List<Window> {
        if (count <= 1 || width < 2 || height < 2) {
            return listOf(Window(0, 0, width, height))
        }
        val winWidth = (width * (1f + overlap) / 2f).toInt().coerceIn(1, width)
        val winHeight = (height * (1f + overlap) / 2f).toInt().coerceIn(1, height)
        val xs = intArrayOf(0, width - winWidth)
        val ys = intArrayOf(0, height - winHeight)
        val result = ArrayList<Window>(4)
        for (y in ys) {
            for (x in xs) {
                result.add(Window(x, y, winWidth, winHeight))
            }
        }
        return result
    }

    fun merge(parts: List<Pair<Window, List<Detection>>>, iouThreshold: Float = 0.45f): List<Detection> {
        val shifted = ArrayList<Detection>()
        for ((window, detections) in parts) {
            for (d in detections) {
                shifted.add(
                    Detection(
                        d.left + window.left,
                        d.top + window.top,
                        d.right + window.left,
                        d.bottom + window.top,
                        d.classIndex,
                        d.score
                    )
                )
            }
        }
        return YoloDecode.nms(shifted, iouThreshold)
    }
}
