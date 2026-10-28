package com.example.wifiradar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WifiScanner(private val context: Context) {

    private val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                _scanResults.value = wifiManager.scanResults
            }
        }
    }

    fun start() {
        context.registerReceiver(
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
        wifiManager.startScan()
    }

    fun stop() {
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    companion object {
        // RSSI на расстоянии 1 м (обычно -50 dBm)
        private const val MEASURED_POWER = -50.0
        // Коэффициент потерь в среде (2.0 для свободного пространства)
        private const val PATH_LOSS_EXPONENT = 2.0

        /**
         * Оценка расстояния по RSSI.
         * Формула: d = 10 ^ ((MeasuredPower - RSSI) / (10 * n))
         */
        fun estimateDistance(rssi: Int): Double {
            return 10.0.pow((MEASURED_POWER - rssi) / (10.0 * PATH_LOSS_EXPONENT))
        }
    }
}

private fun Double.pow(exponent: Double): Double = Math.pow(this, exponent)