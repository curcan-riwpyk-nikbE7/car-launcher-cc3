package com.example.carlauncher.data

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Трип-компьютер: пробег, средняя и максимальная скорость, время в пути.
 *
 * Считается по GPS, который уже подключён ради спидометра — CAN-шина
 * и доступ к бортовому компьютеру не нужны.
 */
object TripComputer {

    private const val PREFS = "car_launcher_trip"
    private const val K_DIST = "trip_distance_m"
    private const val K_TIME = "trip_moving_ms"
    private const val K_MAX = "trip_max_kmh"

    private var prefs: android.content.SharedPreferences? = null
    private var lastLat = Double.NaN
    private var lastLon = Double.NaN
    private var lastAt = 0L

    /** Пройденное расстояние, метры. */
    val distanceM: MutableState<Float> = mutableStateOf(0f)
    /** Время в движении, мс. */
    val movingMs: MutableState<Long> = mutableStateOf(0L)
    /** Максимальная скорость, км/ч. */
    val maxKmh: MutableState<Int> = mutableStateOf(0)

    val averageKmh: Int
        get() {
            val h = movingMs.value / 3_600_000.0
            return if (h > 0.001) ((distanceM.value / 1000.0) / h).toInt() else 0
        }

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        distanceM.value = p.getFloat(K_DIST, 0f)
        movingMs.value = p.getLong(K_TIME, 0L)
        maxKmh.value = p.getInt(K_MAX, 0)
    }

    /**
     * Обновление по новой точке GPS.
     * Точки медленнее 2 км/ч игнорируем — иначе на стоянке
     * дрейф спутников накрутит лишние километры.
     */
    fun onLocation(lat: Double, lon: Double, speedKmh: Int, timeMs: Long) {
        if (speedKmh >= 2) {
            if (!lastLat.isNaN() && lastAt > 0L) {
                val res = FloatArray(1)
                android.location.Location.distanceBetween(lastLat, lastLon, lat, lon, res)
                val step = res[0]
                // Скачок больше километра за тик — потеря сигнала, не движение
                if (step < 1000f) {
                    distanceM.value += step
                    movingMs.value += (timeMs - lastAt).coerceIn(0L, 10_000L)
                }
            }
            if (speedKmh > maxKmh.value) maxKmh.value = speedKmh
            persist()
        }
        lastLat = lat; lastLon = lon; lastAt = timeMs
    }

    fun reset() {
        distanceM.value = 0f
        movingMs.value = 0L
        maxKmh.value = 0
        lastLat = Double.NaN; lastLon = Double.NaN; lastAt = 0L
        persist()
    }

    private fun persist() {
        prefs?.edit()
            ?.putFloat(K_DIST, distanceM.value)
            ?.putLong(K_TIME, movingMs.value)
            ?.putInt(K_MAX, maxKmh.value)
            ?.apply()
    }

    fun formattedDistance(): String {
        val km = distanceM.value / 1000f
        return if (km < 10f) "%.1f".format(km) else km.toInt().toString()
    }

    fun formattedTime(): String {
        val total = movingMs.value / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        return if (h > 0) "${h}ч ${m}м" else "${m}м"
    }
}
