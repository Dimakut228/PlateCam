package com.dimakut228.platecam

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.roundToInt

object BitmapUtils {

    fun crop(bitmap: Bitmap, detection: Detection, margin: Float = 0.08f): Bitmap? {
        val mx = detection.width * margin
        val my = detection.height * margin
        val left = (detection.left - mx).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (detection.top - my).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (detection.right + mx).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (detection.bottom + my).roundToInt().coerceIn(top + 1, bitmap.height)
        if (right - left < 8 || bottom - top < 6) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun isYellow(bitmap: Bitmap): Boolean {
        val x0 = (bitmap.width * 0.2f).roundToInt().coerceIn(0, bitmap.width - 1)
        val x1 = (bitmap.width * 0.8f).roundToInt().coerceIn(x0 + 1, bitmap.width)
        val y0 = (bitmap.height * 0.2f).roundToInt().coerceIn(0, bitmap.height - 1)
        val y1 = (bitmap.height * 0.8f).roundToInt().coerceIn(y0 + 1, bitmap.height)
        val stepX = ((x1 - x0) / 24).coerceAtLeast(1)
        val stepY = ((y1 - y0) / 12).coerceAtLeast(1)
        var count = 0
        var red = 0L
        var green = 0L
        var blue = 0L
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val p = bitmap.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r + g + b > 120) {
                    red += r
                    green += g
                    blue += b
                    count++
                }
                x += stepX
            }
            y += stepY
        }
        if (count == 0) return false
        val ar = red.toFloat() / count
        val ag = green.toFloat() / count
        val ab = blue.toFloat() / count
        return ar > 130f && ag > 100f && ab < 120f && ar - ab > 35f
    }
}
