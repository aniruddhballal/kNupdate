package com.knupdate.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

internal object LocationCapture {
    fun permitted(context: Context): Boolean = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun enabled(context: Context): Boolean = context.getSystemService(LocationManager::class.java).let { manager ->
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).any {
            runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    suspend fun fresh(context: Context): Coordinates {
        check(permitted(context)) { "Location permission is required." }
        val manager = context.getSystemService(LocationManager::class.java)
        val requestedAt = SystemClock.elapsedRealtimeNanos()
        var listener: LocationListener? = null
        try {
            return withTimeout(30_000) {
                suspendCancellableCoroutine { continuation ->
                    val callback = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            // Never fall back to lastKnownLocation, including a cached first callback.
                            if (!continuation.isActive || location.elapsedRealtimeNanos < requestedAt || !location.hasAccuracy()) return
                            val fix = runCatching { Coordinates(location.latitude, location.longitude,
                                location.accuracy.toDouble(), location.time) }.getOrNull() ?: return
                            if (fix.capturedAt > System.currentTimeMillis() + 60_000) return
                            continuation.resume(fix)
                        }
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                        @Deprecated("Deprecated in Android")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    }
                    listener = callback
                    var registered = false
                    for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                        if (runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) {
                            registered = runCatching {
                                manager.requestLocationUpdates(provider, 0L, 0f, callback, Looper.getMainLooper())
                                true
                            }.getOrDefault(false) || registered
                        }
                    }
                    check(registered) { "Turn on phone location and allow location access." }
                }
            }
        } finally { listener?.let { manager.removeUpdates(it) } }
    }
}
