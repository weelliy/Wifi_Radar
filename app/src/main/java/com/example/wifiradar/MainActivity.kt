package com.example.wifiradar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            startScanning()
        } else {
            Toast.makeText(this, "Нужны разрешения для сканирования", Toast.LENGTH_LONG).show()
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
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startScanning() {
        wifiScanner.start()
        Toast.makeText(this, "Сканирование Wi-Fi...", Toast.LENGTH_SHORT).show()
    }

    private fun observeScanResults() {
        lifecycleScope.launch {
            wifiScanner.scanResults.collectLatest { results ->
                val blips = mutableListOf<Pair<Float, Float>>()
                val sb = StringBuilder()

                for (result in results) {
                    val dist = WifiScanner.estimateDistance(result.level)
                    // Угол из BSSID — стабильный и не меняется при пересканировании
                    val angle = (result.BSSID.hashCode() and 0x7FFFFFFF) % 360
                    val normDist = (dist / 50.0).coerceIn(0.05, 1.0).toFloat()
                    blips.add(angle.toFloat() to normDist)

                    sb.append("${result.SSID.ifEmpty { "(скрытая)" }}\n")
                    sb.append("  RSSI: ${result.level} dBm | ~${"%.1f".format(dist)} м\n")
                }

                binding.radarView.setBlips(blips)
                binding.tvNetworks.text = sb.toString()

                // Обновляем высоту
                val loc = locationHelper.getCurrentLocation()
                binding.tvAltitude.text = locationHelper.formatAltitude(loc)

                // Сохраняем самую сильную сеть, если есть локация
                if (loc != null && results.isNotEmpty()) {
                    val strongest = results.maxByOrNull { it.level } ?: return@collectLatest
                    val point = WifiPoint(
                        ssid = strongest.SSID,
                        bssid = strongest.BSSID,
                        rssi = strongest.level,
                        frequency = strongest.frequency,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                        verticalAccuracy = if (loc.hasVerticalAccuracy()) loc.verticalAccuracyMeters else Float.NaN,
                        estimatedDistance = WifiScanner.estimateDistance(strongest.level)
                    )
                    lifecycleScope.launch { db.wifiPointDao().insert(point) }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiScanner.stop()
    }
}