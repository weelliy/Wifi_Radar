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

    private val MAX_RSSI_THRESHOLD = -75

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
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
        
        val notification: Notification = NotificationCompat.Builder(this, "WIFI_RECORDING_CHANNEL")
            .setContentTitle("Wi-Fi Радар: Запись точек")
            .setContentText("Идет фоновое сканирование сетей (каждые 10 сек)...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        // Запускаем как Foreground Service (обязательно для Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }

        // Запускаем цикл сканирования
        serviceScope.launch {
            while (isActive && isRecording) {
                performScanAndSave(floor, description)
                delay(10000) // Пауза 10 секунд между сканированиями
            }
        }
    }

    private suspend fun performScanAndSave(floor: Int, description: String) {
        try {
            // Запрашиваем сканирование
            wifiManager.startScan()
            delay(2000) // Ждем 2 секунды, пока система соберет результаты
            val results = wifiManager.scanResults ?: return

            // Получаем последнюю известную локацию
            val loc = getLastKnownLocation() ?: return

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
                // Сохраняем в базу данных
                withContext(Dispatchers.IO) {
                    points.forEach { db.wifiPointDao().insert(it) }
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки в фоне, чтобы служба не падала
        }
    }

    private fun getLastKnownLocation(): Location? {
        return try {
            // Берем последнюю известную точку (GPS или Сеть)
            val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            // Выбираем ту, что свежее
            if (gps != null && net != null) {
                if (gps.time > net.time) gps else net
            } else {
                gps ?: net
            }
        } catch (e: SecurityException) {
            null
        }
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
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRecording = false
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
