package com.example.wifiradar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Возвращает последнюю известную локацию с высокой точностью.
     * Высота — над эллипсоидом WGS84 (метры).
     * verticalAccuracyMeters — вертикальная погрешность (68% доверительный интервал).
     */
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { cont ->
        if (!hasLocationPermission()) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location -> cont.resume(location) }
            .addOnFailureListener { cont.resume(null) }
    }

    /**
     * Форматирует высоту с учётом погрешности, например: "125.3 м ± 8.0 м"
     */
    fun formatAltitude(location: Location?): String {
        if (location == null || !location.hasAltitude()) return "Высота: н/д"
        val alt = location.altitude
        val acc = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else Float.NaN
        return if (acc.isNaN()) {
            "Высота: %.1f м".format(alt)
        } else {
            "Высота: %.1f м ± %.1f м".format(alt, acc)
        }
    }
}