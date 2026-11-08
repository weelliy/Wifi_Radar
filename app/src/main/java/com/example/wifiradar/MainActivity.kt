package com.example.wifiradar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.wifi.ScanResult
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
    private var rawResults: List<ScanResult> = emptyList()
    private var filteredResults: List<ScanResult> = emptyList()
    private var isAutoRecording = false

    private val MAX_RSSI_THRESHOLD = -75

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            startScanning()
            startLocationUpdates()
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

        // Настройка Spinner (Калибровка)
        binding.spinnerEnvironment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                wifiScanner.pathLossExponent = when(position) {
                    0 -> 2.0 // Улица
                    1 -> 3.0 // Офис
                    else -> 4.0 // Бетон
                }
                observeScanResults() // Пересчитать
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Фильтры частот
        binding.rgFrequency.setOnCheckedChangeListener { _, _ ->
            observeScanResults()
        }

        binding.btnScan.setOnClickListener { checkPermissionsAndScan() }
        binding.btnMarkPoint.setOnClickListener { savePointsInRadius(false) }
        binding.btnAutoRecord.setOnClickListener { toggleAutoRecord() }
        binding.btnHistory.setOnClickListener { showHistoryDialog() }
        
        binding.btnShareCsv.setOnClickListener {
            lifecycleScope.launch {
                val file = ExportHelper.exportCsv(this@MainActivity, db.wifiPointDao().getAll())
                ExportHelper.shareFile(this@MainActivity, file)
            }
        }

        binding.btnClear.setOnClickListener {
            lifecycleScope.launch {
                db.wifiPointDao().clearAll()
                updatePointCount()
                Toast.makeText(this@MainActivity, "База очищена", Toast.LENGTH_SHORT).show()
            }
        }

        updatePointCount()
    }

    private fun checkPermissionsAndScan() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startScanning()
            startLocationUpdates()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startScanning() {
        wifiScanner.start()
        Toast.makeText(this, "Сканирование...", Toast.LENGTH_SHORT).show()
    }

    private fun startLocationUpdates() {
        locationHelper.startListening { loc ->
            currentLocation = loc
            if (loc != null) {
                binding.tvLocation.text = "Координаты: ${"%.6f".format(loc.latitude)}, ${"%.6f".format(loc.longitude)}"
                binding.tvAccuracy.text = locationHelper.formatAccuracy(loc)
                binding.tvAltitude.text = locationHelper.formatAltitude(loc)
            }
        }
    }

    private fun observeScanResults() {
        lifecycleScope.launch {
            wifiScanner.scanResults.collectLatest { results ->
                rawResults = results
                applyFilters()
            }
        }
    }

    private fun applyFilters() {
        val is5GOnly = binding.rb5G.isChecked

        filteredResults = rawResults.filter { result ->
            // Фильтр частоты: 5G это > 5000 МГц
            if (is5GOnly && result.frequency < 5000) return@filter false
            true
        }.sortedWith(compareByDescending<ScanResult> { it.level }.thenBy { wifiScanner.estimateDistance(it.level) })

        val zones = mutableListOf<Triple<Float, String, Int>>()
        val sb = StringBuilder()

        for (result in filteredResults) {
            val dist = wifiScanner.estimateDistance(result.level)
            if (dist < 100.0) {
                zones.add(Triple(dist.toFloat(), result.SSID.ifEmpty { "(скрытая)" }, result.level))
            }
            sb.append("${result.SSID.ifEmpty { "(скрытая)" }} | ${result.level} dBm\n")
        }

        binding.radarView.setWifiZones(zones)
        binding.tvNetworks.text = sb.toString()
    }

    private fun showHistoryDialog() {
        lifecycleScope.launch {
            val points = db.wifiPointDao().getAll()
            if (points.isEmpty()) {
                Toast.makeText(this@MainActivity, "История пуста", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sb = StringBuilder()
            points.take(50).forEach { p -> // Берем последние 50
                sb.append("📍 ${p.ssid} (${p.rssi} dBm)\n")
                sb.append("Этаж: ${p.floor} | ${p.description}\n")
                sb.append("${"%.5f".format(p.latitude)}, ${"%.5f".format(p.longitude)}\n\n")
            }
            
            AlertDialog.Builder(this@MainActivity)
                .setTitle("История точек (последние 50)")
                .setMessage(sb.toString())
                .setPositiveButton("Закрыть", null)
                .show()
        }
    }

    private fun toggleAutoRecord() {
        if (isAutoRecording) {
            val intent = Intent(this, WifiRecordingService::class.java).apply { action = "STOP" }
            startService(intent)
            isAutoRecording = false
            binding.btnAutoRecord.text = "⏺ Авто"
        } else {
            val intent = Intent(this, WifiRecordingService::class.java).apply {
                putExtra("floor", binding.etFloor.text.toString().toIntOrNull() ?: 0)
                putExtra("description", binding.etDescription.text.toString())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            isAutoRecording = true
            binding.btnAutoRecord.text = "⏹ Стоп"
        }
    }

    private fun savePointsInRadius(isAuto: Boolean) {
        val loc = currentLocation ?: return
        val valid = filteredResults.filter { it.level >= MAX_RSSI_THRESHOLD }
        if (valid.isEmpty()) return

        val floor = binding.etFloor.text.toString().toIntOrNull() ?: 0
        val desc = binding.etDescription.text.toString()

        val pointsToSave = valid.map { result ->
            WifiPoint(
                ssid = result.SSID.ifEmpty { "(скрытая)" }, bssid = result.BSSID, rssi = result.level,
                frequency = result.frequency, latitude = loc.latitude, longitude = loc.longitude,
                altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                verticalAccuracy = if (loc.hasVerticalAccuracy()) loc.verticalAccuracyMeters else -1.0f,
                estimatedDistance = wifiScanner.estimateDistance(result.level), floor = floor, description = desc
            )
        }
        lifecycleScope.launch {
            pointsToSave.forEach { db.wifiPointDao().insert(it) }
            updatePointCount()
            Toast.makeText(this@MainActivity, "Сохранено ${pointsToSave.size} сетей", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePointCount() {
        lifecycleScope.launch {
            binding.tvPointsCount.text = "Точек в базе: ${db.wifiPointDao().getCount()}"
        }
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
        updatePointCount()
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
