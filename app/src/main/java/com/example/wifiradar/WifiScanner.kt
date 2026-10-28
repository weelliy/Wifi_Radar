package com.example.wifiradar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow // <-- Исправленный импорт

class WifiScanner(private val context: Context) {

    private val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults

    private var isReceiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                try {
                    _scanResults.value = wifiManager.scanResults
                } catch (e: SecurityException) {
                    // Игнорируем, если нет прав
                }
            }
        }
    }

    fun start() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // ФИКС для Android 13+ (без этого был краш)
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            isReceiverRegistered = true
        }
        try {
            wifiManager.startScan()
        } catch (e: Exception) {
            // Игнорируем ошибку, если сканирование не удалось запустить
        }
    }

    fun stop() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
                isReceiverRegistered = false
            } catch (_: Exception) {}
        }
    }

    companion object {
        private const val MEASURED_POWER = -50.0
        private const val PATH_LOSS_EXPONENT = 2.0

        fun estimateDistance(rssi: Int): Double {
            return 10.0.pow((MEASURED_POWER - rssi) / (10.0 * PATH_LOSS_EXPONENT))
        }
    }
}
