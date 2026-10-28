package com.example.wifiradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
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

    /** Список точек: Pair<угол в градусах, нормализованное расстояние 0..1> */
    private var blips: List<Pair<Float, Float>> = emptyList()

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.9f

        // Фон
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Концентрические круги
        for (i in 1..4) {
            canvas.drawCircle(cx, cy, radius * i / 4f, gridPaint)
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
        for ((angleDeg, normDist) in blips) {
            val a = Math.toRadians(angleDeg.toDouble())
            val d = normDist * radius
            val x = cx + d * cos(a).toFloat()
            val y = cy + d * sin(a).toFloat()
            canvas.drawCircle(x, y, 8f, blipPaint)
        }

        // Центр (устройство)
        canvas.drawCircle(cx, cy, 10f, centerPaint)
    }
}