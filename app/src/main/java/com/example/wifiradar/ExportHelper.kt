package com.example.wifiradar

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ExportHelper {

    private fun exportDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun exportCsv(context: Context, points: List<WifiPoint>): File {
        val file = File(exportDir(context), "wifi_points_${timestamp()}.csv")
        file.bufferedWriter().use { w ->
            w.write("id,ssid,bssid,rssi,frequency,latitude,longitude,altitude,vertical_accuracy,estimated_distance,floor,description,timestamp\n")
            for (p in points) {
                w.write("${p.id},\"${p.ssid.replace("\"", "\"\"")}\",${p.bssid},${p.rssi},${p.frequency},${p.latitude},${p.longitude},${p.altitude},${p.verticalAccuracy},${p.estimatedDistance},${p.floor},\"${p.description.replace("\"", "\"\"")}\",${p.timestamp}\n")
            }
        }
        return file
    }

    suspend fun exportKml(context: Context, points: List<WifiPoint>): File {
        val file = File(exportDir(context), "wifi_points_${timestamp()}.kml")
        file.bufferedWriter().use { w ->
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n<name>Wi-Fi Radar Points</name>\n")
            for (p in points) {
                w.write("<Placemark>\n<name>${escapeXml(p.ssid)}</name>\n<description>BSSID: ${p.bssid}\nRSSI: ${p.rssi} dBm\nВысота: ${p.altitude} м\nЭтаж: ${p.floor}\nОписание: ${escapeXml(p.description)}</description>\n<Point>\n<coordinates>${p.longitude},${p.latitude},${p.altitude}</coordinates>\n</Point>\n</Placemark>\n")
            }
            w.write("</Document>\n</kml>")
        }
        return file
    }

    fun shareFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "csv") "text/csv" else "application/vnd.google-earth.kml+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Отправить файл..."))
    }

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun escapeXml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
