package com.example.wifiradar

import android.content.*
import android.net.wifi.*
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow

class WifiScanner(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults
    private var isReceiverRegistered = false

    // Коэффициент затухания: 2.0 (свободно), 3.0 (офис), 4.0 (бетон)
    var pathLossExponent = 2.0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                try {
                    _scanResults.value = wifiManager.scanResults
                } catch (e: SecurityException) {}
            }
        }
    }

    fun start() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            isReceiverRegistered = true
        }
        try { wifiManager.startScan() } catch (e: Exception) {}
    }

    fun stop() {
        if (isReceiverRegistered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }

    fun estimateDistance(rssi: Int): Double {
        val measuredPower = -50.0
        return 10.0.pow((measuredPower - rssi) / (10.0 * pathLossExponent))
    }
}
