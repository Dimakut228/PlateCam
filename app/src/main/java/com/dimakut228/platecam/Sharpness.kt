package com.dimakut228.platecam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Дисперсия Лапласа по серому изображению — мера резкости.
 * Вынесена в чистый объект, чтобы тестироваться без Android.
 */
internal object LaplacianVariance {
    fun of(gray: FloatArray, width: Int, height: Int): Float {
        if (width < 3 || height < 3) return 0f
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val i = row + x
                val l = 4f * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - width] - gray[i + width]
                sum += l
                sumSq += l * l
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        val variance = sumSq / count - mean * mean
        return if (variance > 0.0) variance.toFloat() else 0f
    }
}

/**
 * Резкость кадра для отбраковки смаза от тряски и движения.
 * В машине кадры размываются сильно и часто: детектор и OCR на них почти
 * не читают номер, поэтому такие кадры пропускаются в пользу более резких.
 */
object Sharpness {

    private const val GRID = 64

    private val paint by lazy { Paint(Paint.FILTER_BITMAP_FLAG) }
    private val buffer by lazy { IntArray(GRID * GRID) }
    private val gray by lazy { FloatArray(GRID * GRID) }
    private val sample by lazy { Bitmap.createBitmap(GRID, GRID, Bitmap.Config.ARGB_8888) }
    private val canvas by lazy { Canvas(sample) }
    private val destination = RectF(0f, 0f, GRID.toFloat(), GRID.toFloat())

    /** Резкость кадра: 0 — полное размытие, сотни — резкий кадр. */
    @Synchronized
    fun of(bitmap: Bitmap): Float {
        canvas.drawBitmap(bitmap, null, destination, paint)
        sample.getPixels(buffer, 0, GRID, 0, 0, GRID, GRID)
        for (i in buffer.indices) {
            val p = buffer[i]
            gray[i] = ((p shr 16) and 0xFF) * 0.299f +
                ((p shr 8) and 0xFF) * 0.587f +
                (p and 0xFF) * 0.114f
        }
        return LaplacianVariance.of(gray, GRID, GRID)
    }
}
