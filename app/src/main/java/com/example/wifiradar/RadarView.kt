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

    private val zoneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44FFEB3B")
        style = Paint.Style.FILL
    }

    private val zoneStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFEB3B")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

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
    private var wifiZones: List<Pair<Float, String>> = emptyList()
    
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

    // ИМЕННО ЭТА ФУНКЦИЯ ТЕПЕРЬ НАЗЫВАЕТСЯ ТАК
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

        canvas.drawCircle(cx, cy, radius, bgPaint)

        val currentMaxDist = maxDistanceMeters / scaleFactor

        for (i in 1..4) {
            val ringRadius = radius * i / 4f
            canvas.drawCircle(cx, cy, ringRadius, gridPaint)
            
            val distLabel = (currentMaxDist * i / 4).toInt()
            canvas.drawText("${distLabel}м", cx + ringRadius - 50f, cy - 10f, textPaint)
        }

        val rad = Math.toRadians(sweepAngle.toDouble())
        canvas.drawLine(
            cx, cy,
            cx + radius * kotlin.math.cos(rad).toFloat(),
            cy + radius * kotlin.math.sin(rad).toFloat(),
            sweepPaint
        )

        val sortedZones = wifiZones.sortedByDescending { it.first }

        for ((distanceMeters, ssid) in sortedZones) {
            val normalizedDist = (distanceMeters / currentMaxDist).coerceIn(0.05f, 1.0f)
            val zoneRadius = normalizedDist * radius
            
            canvas.drawCircle(cx, cy, zoneRadius, zoneFillPaint)
            canvas.drawCircle(cx, cy, zoneRadius, zoneStrokePaint)
            
            if (ssid.isNotEmpty()) {
                canvas.drawText(ssid, cx + (zoneRadius * 0.7f), cy - (zoneRadius * 0.7f), labelPaint)
            }
        }

        canvas.drawCircle(cx, cy, 15f, centerPaint)
    }
}
