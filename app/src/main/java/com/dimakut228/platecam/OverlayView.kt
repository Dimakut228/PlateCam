package com.dimakut228.platecam

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.rgb(0, 230, 140)
        isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }
    private val labelBackground = Paint().apply {
        color = Color.argb(185, 0, 0, 0)
        isAntiAlias = true
    }

    private var detections: List<Detection> = emptyList()
    private var labels: List<String> = emptyList()
    private var sourceWidth = 0
    private var sourceHeight = 0

    fun show(items: List<Detection>, labels: List<String>, width: Int, height: Int) {
        detections = items
        this.labels = labels
        sourceWidth = width
        sourceHeight = height
        invalidate()
    }

    fun clear() {
        detections = emptyList()
        labels = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty() || sourceWidth <= 0 || sourceHeight <= 0) return
        val scale = maxOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        val offsetX = (width - sourceWidth * scale) / 2f
        val offsetY = (height - sourceHeight * scale) / 2f
        detections.forEachIndexed { index, detection ->
            val rect = RectF(
                detection.left * scale + offsetX,
                detection.top * scale + offsetY,
                detection.right * scale + offsetX,
                detection.bottom * scale + offsetY
            )
            canvas.drawRect(rect, boxPaint)
            val label = labels.getOrElse(index) { "" }
            if (label.isNotEmpty()) {
                val textWidth = labelPaint.measureText(label)
                val textY = (rect.top - 10f).coerceAtLeast(labelPaint.textSize + 6f)
                val background = RectF(
                    rect.left,
                    textY - labelPaint.textSize - 6f,
                    rect.left + textWidth + 14f,
                    textY + 7f
                )
                canvas.drawRoundRect(background, 8f, 8f, labelBackground)
                canvas.drawText(label, rect.left + 7f, textY, labelPaint)
            }
        }
    }
}
