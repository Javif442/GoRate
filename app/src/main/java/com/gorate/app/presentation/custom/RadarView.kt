package com.gorate.app.presentation.custom

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

/**
 * ULTRA-ROBUST Radar View.
 * Uses a manual Handler loop to bypass system animation throttling and "Auto Hz" issues.
 */
class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
        alpha = 40
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }

    private var sweepAngle = 0f
    private var isAnimating = false
    private var rotationStep = 5f

    private val mainHandler = Handler(Looper.getMainLooper())
    private val animRunnable = object : Runnable {
        override fun run() {
            if (!isAnimating) return
            sweepAngle = (sweepAngle + rotationStep) % 360f
            invalidate()
            mainHandler.postDelayed(this, 28) // ~35 FPS (Ultra-eficiente en CPU)
        }
    }

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    fun startRadar(turbo: Boolean = false) {
        rotationStep = if (turbo) 14f else 5f
        
        if (isAnimating) return 
        
        isAnimating = true
        mainHandler.removeCallbacks(animRunnable)
        mainHandler.post(animRunnable)
    }

    fun stopRadar() {
        isAnimating = false
        mainHandler.removeCallbacks(animRunnable)
        sweepAngle = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopRadar()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            centerX = w / 2f
            centerY = h / 2f
            radius = w.coerceAtMost(h) / 2f * 0.95f

            val colors = intArrayOf(
                Color.TRANSPARENT,
                Color.argb(0, 255, 255, 255),
                Color.argb(70, 255, 255, 255),
                Color.argb(190, 255, 255, 255)
            )
            val positions = floatArrayOf(0.0f, 0.2f, 0.6f, 1.0f)
            sweepPaint.shader = SweepGradient(centerX, centerY, colors, positions)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (centerX == 0f || radius == 0f) return

        ringPaint.alpha = 40
        canvas.drawCircle(centerX, centerY, radius * 0.33f, ringPaint)
        canvas.drawCircle(centerX, centerY, radius * 0.66f, ringPaint)
        canvas.drawCircle(centerX, centerY, radius, ringPaint)

        if (isAnimating || sweepAngle != 0f) {
            canvas.save()
            canvas.rotate(sweepAngle - 90f, centerX, centerY)
            canvas.drawCircle(centerX, centerY, radius, sweepPaint)
            
            linePaint.alpha = 230 
            canvas.drawLine(centerX, centerY, centerX + radius, centerY, linePaint)
            canvas.restore()
        }

        centerPaint.alpha = 255
        canvas.drawCircle(centerX, centerY, 6f, centerPaint)
    }
}
