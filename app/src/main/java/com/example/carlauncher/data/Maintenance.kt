package com.example.carlauncher.data

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Напоминание о ТО и журнал поездок.
 *
 * Пробег считаем по GPS — с шины автомобиля лаунчер данные взять не
 * может, для этого нужны права прошивки и доступ к CAN. GPS даёт
 * погрешность около 2-3%: для напоминания «пора менять масло» этого
 * достаточно, для одометра — нет. Поэтому цифру честно называем
 * «пробег по GPS», а не «пробег автомобиля».
 */
object Maintenance {

    private const val PREFS = "car_launcher_trip"

    private const val K_TOTAL = "odo_total_m"        // накопленный пробег, метры
    private const val K_SERVICE_AT = "service_at_m"  // на каком пробеге было ТО
    private const val K_INTERVAL = "service_interval_km"
    private const val K_ENABLED = "service_on"

    private const val K_TRIPS = "trips_json"

    private var prefs: android.content.SharedPreferences? = null

    /** Общий пробег по GPS, метры. */
    val totalM: MutableState<Float> = mutableFloatStateOf(0f)

    /** Интервал ТО в километрах. */
    val intervalKm: MutableState<Int> = mutableIntStateOf(10_000)

    /** Пробег на момент последнего ТО, метры. */
    val serviceAtM: MutableState<Float> = mutableFloatStateOf(0f)

    val enabled: MutableState<Boolean> = mutableStateOf(false)

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        totalM.value = p.getFloat(K_TOTAL, 0f)
        serviceAtM.value = p.getFloat(K_SERVICE_AT, 0f)
        intervalKm.value = p.getInt(K_INTERVAL, 10_000)
        enabled.value = p.getBoolean(K_ENABLED, false)
    }

    /** Сколько километров осталось до ТО. Отрицательное — просрочено. */
    val kmLeft: Int
        get() {
            val driven = (totalM.value - serviceAtM.value) / 1000f
            return (intervalKm.value - driven).toInt()
        }

    /** Добавить пройденное расстояние. Зовётся из трип-компьютера. */
    fun addDistance(meters: Float) {
        if (meters <= 0f) return
        totalM.value += meters
        prefs?.edit()?.putFloat(K_TOTAL, totalM.value)?.apply()
    }

    fun setInterval(km: Int) {
        intervalKm.value = km.coerceIn(1000, 100_000)
        prefs?.edit()?.putInt(K_INTERVAL, intervalKm.value)?.apply()
    }

    fun setEnabled(v: Boolean) {
        enabled.value = v
        prefs?.edit()?.putBoolean(K_ENABLED, v)?.apply()
    }

    /** Отметить, что ТО пройдено сейчас. */
    fun markServiced() {
        serviceAtM.value = totalM.value
        prefs?.edit()?.putFloat(K_SERVICE_AT, serviceAtM.value)?.apply()
    }

    /** Задать текущий пробег вручную — по одометру автомобиля. */
    fun setTotalKm(km: Int) {
        totalM.value = km * 1000f
        prefs?.edit()?.putFloat(K_TOTAL, totalM.value)?.apply()
    }

    // ─────────────────────────── журнал поездок ───────────────────────────

    data class Trip(
        val startedAt: Long,
        val distanceKm: Float,
        val minutes: Int,
        val avgKmh: Int,
        val maxKmh: Int
    )

    /**
     * Последние поездки. Держим 30 штук: этого хватает на пару недель,
     * а хранилище настроек не место для длинной истории.
     */
    fun trips(): List<Trip> = runCatching {
        val raw = prefs?.getString(K_TRIPS, "[]") ?: "[]"
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Trip(
                startedAt = o.getLong("t"),
                distanceKm = o.getDouble("d").toFloat(),
                minutes = o.getInt("m"),
                avgKmh = o.getInt("a"),
                maxKmh = o.getInt("x")
            )
        }.reversed()
    }.getOrDefault(emptyList())

    fun addTrip(distanceKm: Float, minutes: Int, avgKmh: Int, maxKmh: Int) {
        // Короткие отрезки не пишем: перестановка во дворе поездкой
        // не является и только засоряет список.
        if (distanceKm < 0.5f || minutes < 2) return
        runCatching {
            val arr = org.json.JSONArray(prefs?.getString(K_TRIPS, "[]") ?: "[]")
            arr.put(
                org.json.JSONObject()
                    .put("t", System.currentTimeMillis())
                    .put("d", distanceKm.toDouble())
                    .put("m", minutes)
                    .put("a", avgKmh)
                    .put("x", maxKmh)
            )
            // Обрезаем старое с начала
            val trimmed = org.json.JSONArray()
            val from = (arr.length() - 30).coerceAtLeast(0)
            for (i in from until arr.length()) trimmed.put(arr.get(i))
            prefs?.edit()?.putString(K_TRIPS, trimmed.toString())?.apply()
        }
    }

    fun clearTrips() {
        prefs?.edit()?.remove(K_TRIPS)?.apply()
    }
}
