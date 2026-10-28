package com.example.wifiradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B3D2E")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B5E20")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#81C784")
        textSize = 30f
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val blipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var sweepAngle = 0f
    private var blips: List<Pair<Float, Float>> = emptyList()
    
    // Базовое расстояние радара (до зума) - 50 метров
    private var maxDistanceMeters = 50.0f
    private var scaleFactor = 1.0f
    private val minScale = 0.2f // Увеличение до 10 метров
    private val maxScale = 2.0f // Отдаление до 100 метров

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(minScale, maxScale)
            invalidate()
            return true
        }
    })

    fun setBlips(newBlips: List<Pair<Float, Float>>) {
        blips = newBlips
        invalidate()
    }

    fun startSweep() {
        postOnAnimation(object : Runnable {
            override fun run() {
                sweepAngle = (sweepAngle + 3f) % 360f
                invalidate()
                postOnAnimation(this)
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.9f

        // Фон
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Текущая максимальная дистанция с учетом зума
        val currentMaxDist = maxDistanceMeters / scaleFactor

        // Концентрические круги (4 кольца)
        for (i in 1..4) {
            val ringRadius = radius * i / 4f
            canvas.drawCircle(cx, cy, ringRadius, gridPaint)
            
            // Подпись дистанции для каждого кольца
            val distLabel = (currentMaxDist * i / 4).toInt()
            canvas.drawText("${distLabel}м", cx + ringRadius - 50f, cy - 10f, textPaint)
        }

        // Линии осей
        canvas.drawLine(cx - radius, cy, cx + radius, cy, gridPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, gridPaint)

        // Вращающийся луч
        val rad = Math.toRadians(sweepAngle.toDouble())
        canvas.drawLine(
            cx, cy,
            cx + radius * cos(rad).toFloat(),
            cy + radius * sin(rad).toFloat(),
            sweepPaint
        )

        // Точки Wi-Fi
        for ((angleDeg, distanceMeters) in blips) {
            val a = Math.toRadians(angleDeg.toDouble())
            // Нормализуем дистанцию относительно текущего масштаба
            val normalizedDist = (distanceMeters / currentMaxDist).coerceIn(0.05f, 1.0f)
            val d = normalizedDist * radius
            val x = cx + d * cos(a).toFloat()
            val y = cy + d * sin(a).toFloat()
            canvas.drawCircle(x, y, 12f, blipPaint)
        }

        // Центр (устройство)
        canvas.drawCircle(cx, cy, 15f, centerPaint)
    }
}
