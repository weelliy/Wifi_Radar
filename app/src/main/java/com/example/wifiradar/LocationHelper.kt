package com.example.wifiradar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

class LocationHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @Suppress("MissingPermission")
    fun getCurrentLocation(): Location? {
        if (!hasLocationPermission() || !isLocationEnabled()) return null
        
        // Получаем последнюю известную локацию (работает быстрее и стабильнее)
        val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        
        return if (lastGps != null && lastNet != null) {
            if (lastGps.time > lastNet.time) lastGps else lastNet
        } else {
            lastGps ?: lastNet
        }
    }

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
