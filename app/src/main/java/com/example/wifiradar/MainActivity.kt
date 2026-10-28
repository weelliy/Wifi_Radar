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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var wifiScanner: WifiScanner
    private lateinit var locationHelper: LocationHelper
    private lateinit var db: AppDatabase

    private var currentLocation: Location? = null
    private var currentResults: List<ScanResult> = emptyList()

    private var isAutoRecording = false
    private var autoRecordJob: Job? = null

    // НОВОЕ: Порог силы сигнала. 
    // -75 dBm примерно равно 20 метрам в открытом пространстве. 
    // Все, что слабее (например, -80, -90), будет отсекаться.
    private val MAX_RSSI_THRESHOLD = -75

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
        
        binding.btnMarkPoint.setOnClickListener { 
            savePointsInRadius(isAuto = false) 
        }

        binding.btnAutoRecord.setOnClickListener {
            if (isAutoRecording) {
                stopAutoRecording()
            } else {
                startAutoRecording()
            }
        }

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
                binding.tvAccuracy.text = locationHelper.formatAccuracy(loc)
                binding.tvAltitude.text = locationHelper.formatAltitude(loc)
            } else {
                binding.tvLocation.text = "Координаты: нет сигнала GPS"
                binding.tvAccuracy.text = "Точность: н/д"
                binding.tvAltitude.text = "Высота: нет сигнала GPS"
            }
        }
    }

    private fun observeScanResults() {
        lifecycleScope.launch {
            wifiScanner.scanResults.collectLatest { results ->
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
                    sb.append("  Расстояние: ~${"%.1f".format(dist)} м\n")
                }

                binding.radarView.setBlips(blips)
                binding.tvNetworks.text = sb.toString()
            }
        }
    }

    private fun startAutoRecording() {
        if (!locationHelper.isLocationEnabled()) {
            Toast.makeText(this, "Включите GPS для автозаписи!", Toast.LENGTH_SHORT).show()
            return
        }
        isAutoRecording = true
        binding.btnAutoRecord.text = "⏹ Стоп"
        binding.btnAutoRecord.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
        
        autoRecordJob = lifecycleScope.launch {
            while (isActive && isAutoRecording) {
                savePointsInRadius(isAuto = true)
                delay(10000) // 10 секунд
            }
        }
        Toast.makeText(this, "Автозапись включена (каждые 10 сек)", Toast.LENGTH_SHORT).show()
    }

    private fun stopAutoRecording() {
        isAutoRecording = false
        autoRecordJob?.cancel()
        autoRecordJob = null
        binding.btnAutoRecord.text = "⏺ Авто"
        binding.btnAutoRecord.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_blue_dark)
        Toast.makeText(this, "Автозапись остановлена", Toast.LENGTH_SHORT).show()
    }

    private fun savePointsInRadius(isAuto: Boolean) {
        if (currentLocation == null) {
            if (!isAuto) Toast.makeText(this, "Ожидание GPS...", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentResults.isEmpty()) {
            if (!isAuto) Toast.makeText(this, "Сначала нажмите 'Скан'", Toast.LENGTH_SHORT).show()
            return
        }

        val floorStr = binding.etFloor.text.toString()
        val currentFloor = floorStr.toIntOrNull() ?: 0
        val currentDescription = binding.etDescription.text.toString()

        // ФИЛЬТРАЦИЯ ПО RSSI: 
        // Берем сети, у которых уровень сигнала >= -75 dBm (то есть -40, -50, -60...)
        val validNetworks = currentResults.filter { 
            it.level >= MAX_RSSI_THRESHOLD 
        }

        if (validNetworks.isEmpty()) {
            if (!isAuto) Toast.makeText(this, "Нет сетей с сигналом лучше $MAX_RSSI_THRESHOLD dBm", Toast.LENGTH_SHORT).show()
            return
        }

        val loc = currentLocation!!
        val pointsToSave = validNetworks.map { result ->
            WifiPoint(
                ssid = result.SSID.ifEmpty { "(скрытая)" },
                bssid = result.BSSID,
                rssi = result.level, // Записываем силу сигнала от текущей точки
                frequency = result.frequency,
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                verticalAccuracy = if (loc.hasVerticalAccuracy()) loc.verticalAccuracyMeters else -1.0f,
                estimatedDistance = WifiScanner.estimateDistance(result.level),
                floor = currentFloor,
                description = currentDescription
            )
        }

        lifecycleScope.launch {
            pointsToSave.forEach { db.wifiPointDao().insert(it) }
            if (!isAuto) {
                Toast.makeText(this@MainActivity, "Сохранено ${pointsToSave.size} сетей (сигнал > $MAX_RSSI_THRESHOLD dBm)", Toast.LENGTH_LONG).show()
                binding.etDescription.text?.clear()
            }
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
        if (isAutoRecording) {
            stopAutoRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiScanner.stop()
        locationHelper.stopListening()
    }
}
