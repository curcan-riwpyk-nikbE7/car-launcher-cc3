package com.example.carlauncher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Текущая скорость по GPS.
 * Без разрешения ACCESS_FINE_LOCATION просто останется 0 — карточка это переживёт.
 */
@Composable
fun rememberSpeedKmh(): State<Int> {
    val context = LocalContext.current
    val speed = remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) return@DisposableEffect onDispose { }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@DisposableEffect onDispose { }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Location.speed в м/с; отсекаем GPS-дрейф на стоянке
                val kmh = (location.speed * 3.6f).toInt()
                speed.value = if (kmh < 2) 0 else kmh
                TripComputer.onLocation(
                    location.latitude, location.longitude,
                    speed.value, System.currentTimeMillis()
                )
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) { speed.value = 0 }
        }

        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
        }

        onDispose { runCatching { lm.removeUpdates(listener) } }
    }
    return speed
}
