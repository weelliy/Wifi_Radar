package com.example.wifiradar

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.net.wifi.ScanResult
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.wifiradar.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var wifiScanner: WifiScanner
    private lateinit var locationHelper: LocationHelper
    private lateinit var db: AppDatabase

    private var currentLocation: Location? = null
    private var currentResults: List<ScanResult> = emptyList()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            startScanning()
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Нужны разрешения для сканирования и GPS", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        wifiScanner = WifiScanner(this)
        locationHelper = LocationHelper(this)

        binding.radarView.startSweep()

        binding.btnScan.setOnClickListener { checkPermissionsAndScan() }
        
        binding.btnMarkPoint.setOnClickListener { markCurrentPoint() }

        binding.btnExportCsv.setOnClickListener {
            lifecycleScope.launch {
                ExportHelper.exportCsv(this@MainActivity, db.wifiPointDao().getAll())
            }
        }

        binding.btnExportKml.setOnClickListener {
            lifecycleScope.launch {
                ExportHelper.exportKml(this@MainActivity, db.wifiPointDao().getAll())
            }
        }

        binding.btnClear.setOnClickListener {
            lifecycleScope.launch {
                db.wifiPointDao().clearAll()
                Toast.makeText(this@MainActivity, "База очищена", Toast.LENGTH_SHORT).show()
            }
        }

        observeScanResults()
    }

    private fun checkPermissionsAndScan() {
        if (!locationHelper.isLocationEnabled()) {
            Toast.makeText(this, "Пожалуйста, включите Геолокацию (GPS) в шторке телефона!", Toast.LENGTH_LONG).show()
            return
        }

        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        
        if (needed.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startScanning()
            startLocationUpdates()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startScanning() {
        try {
            wifiScanner.start()
            Toast.makeText(this, "Сканирование...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка Wi-Fi: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startLocationUpdates() {
        locationHelper.startListening { loc ->
            currentLocation = loc
            if (loc != null) {
                binding.tvLocation.text = "Координаты: ${"%.6f".format(loc.latitude)}, ${"%.6f".format(loc.longitude)}"
                binding.tvAltitude.text = locationHelper.formatAltitude(loc)
            } else {
                binding.tvLocation.text = "Координаты: нет сигнала GPS"
                binding.tvAltitude.text = "Высота: нет сигнала GPS"
            }
        }
    }

    private fun observeScanResults() {
        lifecycleScope.launch {
            wifiScanner.scanResults.collectLatest { results ->
                
                // СОРТИРОВКА: Сначала самые сильные (близкие к 0), потом по дистанции
                val sortedResults = results.sortedWith(
                    compareByDescending<ScanResult> { it.level }
                        .thenBy { WifiScanner.estimateDistance(it.level) }
                )
                
                currentResults = sortedResults
                val blips = mutableListOf<Pair<Float, Float>>()
                val sb = StringBuilder()

                for (result in sortedResults) {
                    val dist = WifiScanner.estimateDistance(result.level)
                    val angle = (result.BSSID.hashCode() and 0x7FFFFFFF) % 360
                    blips.add(angle.toFloat() to dist.toFloat())

                    sb.append("${result.SSID.ifEmpty { "(скрытая)" }}\n")
                    sb.append("  BSSID: ${result.BSSID} | RSSI: ${result.level} dBm\n")
                    sb.append("  Примерное расстояние: ~${"%.1f".format(dist)} м\n")
                }

                binding.radarView.setBlips(blips)
                binding.tvNetworks.text = sb.toString()
            }
        }
    }

    private fun markCurrentPoint() {
        if (currentLocation == null) {
            Toast.makeText(this, "Ожидание GPS... Выйдите на улицу или подождите.", Toast.LENGTH_LONG).show()
            return
        }

        if (currentResults.isEmpty()) {
            Toast.makeText(this, "Сначала нажмите 'Сканировать', чтобы найти сети", Toast.LENGTH_SHORT).show()
            return
        }

        // Берем этаж из текстового поля. Если пусто, ставим 0.
        val floorStr = binding.etFloor.text.toString()
        val currentFloor = floorStr.toIntOrNull() ?: 0

        // Берем самую сильную сеть прямо сейчас (она первая в списке после сортировки)
        val strongest = currentResults.firstOrNull() ?: return
        val loc = currentLocation!!

        val point = WifiPoint(
            ssid = strongest.SSID.ifEmpty { "(скрытая)" },
            bssid = strongest.BSSID,
            rssi = strongest.level,
            frequency = strongest.frequency,
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
            verticalAccuracy = if (loc.hasVerticalAccuracy()) loc.verticalAccuracyMeters else -1.0f,
            estimatedDistance = WifiScanner.estimateDistance(strongest.level),
            floor = currentFloor // <-- Записываем этаж
        )

        lifecycleScope.launch {
            db.wifiPointDao().insert(point)
            Toast.makeText(this@MainActivity, "Точка сохранена! Сеть: ${point.ssid} (Этаж: $currentFloor)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (locationHelper.hasLocationPermission() && locationHelper.isLocationEnabled()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        locationHelper.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiScanner.stop()
        locationHelper.stopListening()
    }
}
