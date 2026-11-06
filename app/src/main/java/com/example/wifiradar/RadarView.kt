package com.example.wifiradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

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

    // Кисть для рисования зон (полупрозрачная заливка)
    private val zoneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44FFEB3B") // Полупрозрачный желтый
        style = Paint.Style.FILL
    }

    // Кисть для контура зон
    private val zoneStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFEB3B") // Яркий желтый
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // Кисть для текста (SSID) над кольцом
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 25f
        textAlign = Paint.Align.CENTER
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var sweepAngle = 0f
    
    // Список сетей: (расстояние в метрах, имя сети)
    private var wifiZones: List<Pair<Float, String>> = emptyList()
    
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

    fun setWifiZones(newZones: List<Pair<Float, String>>) {
        wifiZones = newZones
        invalidate()
    }

    fun startSweep() {
        postOnAnimation(object : Runnable {
            override fun run() {
                sweepAngle = (sweepAngle + 2f) % 360f
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

        // Концентрические круги сетки (4 кольца)
        for (i in 1..4) {
            val ringRadius = radius * i / 4f
            canvas.drawCircle(cx, cy, ringRadius, gridPaint)
            
            // Подпись дистанции для каждого кольца
            val distLabel = (currentMaxDist * i / 4).toInt()
            canvas.drawText("${distLabel}м", cx + ringRadius - 50f, cy - 10f, textPaint)
        }

        // Вращающийся луч (для красоты)
        val rad = Math.toRadians(sweepAngle.toDouble())
        canvas.drawLine(
            cx, cy,
            cx + radius * kotlin.math.cos(rad).toFloat(),
            cy + radius * kotlin.math.sin(rad).toFloat(),
            sweepPaint
        )

        // РИСУЕМ ЗОНЫ СЕТЕЙ
        // Сортируем зоны по убыванию радиуса, чтобы большие рисовались сзади, а маленькие (близкие) поверх них
        val sortedZones = wifiZones.sortedByDescending { it.first }

        for ((distanceMeters, ssid) in sortedZones) {
            val normalizedDist = (distanceMeters / currentMaxDist).coerceIn(0.05f, 1.0f)
            val zoneRadius = normalizedDist * radius
            
            // Рисуем полупрозрачный круг (зону)
            canvas.drawCircle(cx, cy, zoneRadius, zoneFillPaint)
            // Рисуем контур
            canvas.drawCircle(cx, cy, zoneRadius, zoneStrokePaint)
            
            // Пишем название сети над зоной (справа от оси Y)
            if (ssid.isNotEmpty()) {
                // Немного поднимаем текст над линией кольца
                canvas.drawText(ssid, cx + (zoneRadius * 0.7f), cy - (zoneRadius * 0.7f), labelPaint)
            }
        }

        // Центр (устройство)
        canvas.drawCircle(cx, cy, 15f, centerPaint)
    }
}
