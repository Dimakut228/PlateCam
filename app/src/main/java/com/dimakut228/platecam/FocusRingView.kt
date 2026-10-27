package com.dimakut228.platecam

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class FocusRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        color = Color.WHITE
    }

    private var centerX = 0f
    private var centerY = 0f
    private var progress = 1f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 650L
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    fun show(x: Float, y: Float) {
        centerX = x
        centerY = y
        animator.cancel()
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (progress >= 1f) return
        val radius = dp(26f) + dp(12f) * progress
        ringPaint.alpha = ((1f - progress) * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(centerX, centerY, radius, ringPaint)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
