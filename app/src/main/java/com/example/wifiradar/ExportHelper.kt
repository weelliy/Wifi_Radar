package com.example.wifiradar

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    private fun exportDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun exportCsv(context: Context, points: List<WifiPoint>) {
        val file = File(exportDir(context), "wifi_points_${timestamp()}.csv")
        file.bufferedWriter().use { w ->
            w.write("id,ssid,bssid,rssi,frequency,latitude,longitude,altitude,vertical_accuracy,estimated_distance,timestamp\n")
            for (p in points) {
                w.write(
                    "${p.id},\"${p.ssid.replace("\"", "\"\"")}\",${p.bssid}," +
                    "${p.rssi},${p.frequency},${p.latitude},${p.longitude}," +
                    "${p.altitude},${p.verticalAccuracy},${p.estimatedDistance},${p.timestamp}\n"
                )
            }
        }
        notifyExported(context, file)
    }

    suspend fun exportKml(context: Context, points: List<WifiPoint>) {
        val file = File(exportDir(context), "wifi_points_${timestamp()}.kml")
        file.bufferedWriter().use { w ->
            w.write("""<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>Wi-Fi Radar Points</name>
""")
            for (p in points) {
                w.write("""
    <Placemark>
      <name>${escapeXml(p.ssid)}</name>
      <description>BSSID: ${p.bssid}
RSSI: ${p.rssi} dBm
Высота: ${p.altitude} м ± ${p.verticalAccuracy} м
Оценка расстояния: ${"%.1f".format(p.estimatedDistance)} м</description>
      <Point>
        <coordinates>${p.longitude},${p.latitude},${p.altitude}</coordinates>
      </Point>
    </Placemark>
""")
            }
            w.write("  </Document>\n</kml>")
        }
        notifyExported(context, file)
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun notifyExported(context: Context, file: File) {
        android.widget.Toast.makeText(
            context,
            "Экспортировано: ${file.absolutePath}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}