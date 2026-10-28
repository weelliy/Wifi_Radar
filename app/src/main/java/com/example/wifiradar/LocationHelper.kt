package com.example.wifiradar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

class LocationHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: ((Location?) -> Unit)? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            listener?.invoke(location)
        }
        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

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
    fun startListening(onLocationUpdate: (Location?) -> Unit) {
        if (!hasLocationPermission() || !isLocationEnabled()) {
            onLocationUpdate(null)
            return
        }
        listener = onLocationUpdate
        
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                locationListener,
                context.mainLooper
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                1000L,
                1f,
                locationListener,
                context.mainLooper
            )
        } catch (e: Exception) {
            onLocationUpdate(null)
        }
    }

    fun stopListening() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (_: Exception) {}
        listener = null
    }

    fun formatAccuracy(location: Location?): String {
        if (location == null || !location.hasAccuracy()) return "Точность: н/д"
        return "Точность: ~${location.accuracy.toInt()} м"
    }

    fun formatAltitude(location: Location?): String {
        if (location == null || !location.hasAltitude()) return "Высота: н/д (ждем GPS)"
        val alt = location.altitude
        val acc = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else Float.NaN
        return if (acc.isNaN()) {
            "Высота: %.1f м".format(alt)
        } else {
            "Высота: %.1f м ± %.1f м".format(alt, acc)
        }
    }
}
