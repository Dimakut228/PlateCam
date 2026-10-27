package com.dimakut228.platecam

data class Detection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val classIndex: Int,
    val score: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun iou(other: Detection): Float {
        val x1 = maxOf(left, other.left)
        val y1 = maxOf(top, other.top)
        val x2 = minOf(right, other.right)
        val y2 = minOf(bottom, other.bottom)
        val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val union = width * height + other.width * other.height - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun distanceTo(other: Detection): Float {
        val dx = centerX - other.centerX
        val dy = centerY - other.centerY
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
