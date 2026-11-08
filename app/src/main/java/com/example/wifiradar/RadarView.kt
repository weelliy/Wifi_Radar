package com.example.wifiradar

import android.content.Context
import android.graphics.*
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
        color = Color.parseColor("#050B14") // Глубокий космический синий
        style = Paint.Style.FILL
    }

    // Пунктирные кольца
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E3A5F")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF") // Неоновый голубой
        textSize = 28f
        typeface = Typeface.MONOSPACE
    }

    // Кисти для зон (зависят от сигнала)
    private val zoneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val zoneStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.SOLID) // Свечение
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.SOLID)
    }

    private var sweepAngle = 0f
    private var wifiZones: List<Triple<Float, String, Int>> = emptyList() // (Расстояние, SSID, RSSI)
    
    private var maxDistanceMeters = 50.0f
    private var scaleFactor = 1.0f
    private val minScale = 0.2f 
    private val maxScale = 2.0f 

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(minScale, maxScale)
            invalidate()
            return true
        }
    })

    fun setWifiZones(newZones: List<Triple<Float, String, Int>>) {
        wifiZones = newZones
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

        // Фон с градиентом
        val bgShader = RadialGradient(cx, cy, radius * 1.5f, Color.parseColor("#0B1B33"), Color.parseColor("#050B14"), Shader.TileMode.CLAMP)
        bgPaint.shader = bgShader
        canvas.drawCircle(cx, cy, radius * 1.5f, bgPaint)

        val currentMaxDist = maxDistanceMeters / scaleFactor

        // Сетка
        for (i in 1..4) {
            val ringRadius = radius * i / 4f
            canvas.drawCircle(cx, cy, ringRadius, gridPaint)
            val distLabel = (currentMaxDist * i / 4).toInt()
            canvas.drawText("${distLabel}м", cx + ringRadius - 45f, cy - 10f, textPaint)
        }

        // Луч радара (Градиентный)
        val sweepRadStart = Math.toRadians((sweepAngle - 45).toDouble())
        val sweepRadEnd = Math.toRadians(sweepAngle.toDouble())
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweepShader = SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT, Color.parseColor("#8000E5FF")), floatArrayOf(0f, 1f))
        // Вращаем шейдер
        val matrix = Matrix()
        matrix.setRotate(sweepAngle, cx, cy)
        sweepShader.setLocalMatrix(matrix)
        sweepPaint.shader = sweepShader
        
        canvas.drawArc(rect, sweepAngle - 45, 45f, true, sweepPaint)

        // Рисуем зоны
        val sortedZones = wifiZones.sortedByDescending { it.first }

        for ((distanceMeters, ssid, rssi) in sortedZones) {
            val normalizedDist = (distanceMeters / currentMaxDist).coerceIn(0.05f, 1.0f)
            val zoneRadius = normalizedDist * radius
            
            // Цвет зависит от сигнала
            val color = when {
                rssi >= -50 -> Color.parseColor("#39FF14") // Зеленый (Отлично)
                rssi >= -70 -> Color.parseColor("#FFD700") // Желтый (Хорошо)
                else -> Color.parseColor("#FF073A")        // Красный (Слабо)
            }
            
            zoneFillPaint.color = Color.argb(30, Color.red(color), Color.green(color), Color.blue(color))
            zoneStrokePaint.color = color
            
            canvas.drawCircle(cx, cy, zoneRadius, zoneFillPaint)
            canvas.drawCircle(cx, cy, zoneRadius, zoneStrokePaint)
            
            if (ssid.isNotEmpty()) {
                labelPaint.color = color
                canvas.drawText(ssid, cx + (zoneRadius * 0.7f), cy - (zoneRadius * 0.7f), labelPaint)
            }
        }

        // Центр
        canvas.drawCircle(cx, cy, 10f, centerPaint)
    }
}
