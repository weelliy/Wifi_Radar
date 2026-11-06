package com.example.wifiradar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WifiRecordingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isRecording = false
    private lateinit var db: AppDatabase
    private lateinit var wifiManager: WifiManager
    private lateinit var locationManager: LocationManager
    private lateinit var notificationManager: NotificationManager

    private val MAX_RSSI_THRESHOLD = -75
    private val MAX_ACCURACY_METERS = 3.0f // Максимальная погрешность GPS для сохранения

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopRecording()
            return START_NOT_STICKY
        }

        val floor = intent?.getIntExtra("floor", 0) ?: 0
        val description = intent?.getStringExtra("description") ?: ""

        if (!isRecording) {
            startRecording(floor, description)
        }
        return START_STICKY
    }

    private fun startRecording(floor: Int, description: String) {
        isRecording = true
        createNotificationChannel()
        
        val initialNotification = buildNotification("Инициализация...", "Ожидание GPS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, initialNotification)
        }

        serviceScope.launch {
            while (isActive && isRecording) {
                val loc = getLastKnownLocation()
                val accuracy = if (loc != null && loc.hasAccuracy()) loc.accuracy else 999f

                if (accuracy <= MAX_ACCURACY_METERS) {
                    // Точность хорошая (0-3 метра) - сохраняем точки
                    val savedCount = performScanAndSave(floor, description, loc!!)
                    updateNotification("Запись активна", "Точность ~${accuracy.toInt()}м. Сохранено сетей: $savedCount")
                    
                    // Ждем обычный интервал (10 секунд)
                    delay(10000) 
                } else {
                    // Точность плохая (больше 3 метров) - не сохраняем, ждем 1 секунду
                    updateNotification("Ожидание GPS...", "Точность ~${accuracy.toInt()}м (нужно <= 3м)")
                    
                    // Проверяем чаще (раз в секунду)
                    delay(1000) 
                }
            }
        }
    }

    private suspend fun performScanAndSave(floor: Int, description: String, loc: Location): Int {
        try {
            wifiManager.startScan()
            delay(2000) // Ждем завершения сканирования
            val results = wifiManager.scanResults ?: return 0

            val validNetworks = results.filter { it.level >= MAX_RSSI_THRESHOLD }

            if (validNetworks.isNotEmpty()) {
                val points = validNetworks.map { result ->
                    WifiPoint(
                        ssid = result.SSID.ifEmpty { "(скрытая)" },
                        bssid = result.BSSID,
                        rssi = result.level,
                        frequency = result.frequency,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                        verticalAccuracy = if (loc.hasVerticalAccuracy()) loc.verticalAccuracyMeters else -1.0f,
                        estimatedDistance = WifiScanner.estimateDistance(result.level),
                        floor = floor,
                        description = description
                    )
                }
                withContext(Dispatchers.IO) {
                    points.forEach { db.wifiPointDao().insert(it) }
                }
                return points.size
            }
            return 0
        } catch (e: Exception) {
            return 0
        }
    }

    private fun getLastKnownLocation(): Location? {
        return try {
            val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (gps != null && net != null) {
                if (gps.time > net.time) gps else net
            } else {
                gps ?: net
            }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, "WIFI_RECORDING_CHANNEL")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        notificationManager.notify(1, notification)
    }

    private fun stopRecording() {
        isRecording = false
        serviceScope.coroutineContext[Job]?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "WIFI_RECORDING_CHANNEL",
                "Фоновая запись Wi-Fi",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRecording = false
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
