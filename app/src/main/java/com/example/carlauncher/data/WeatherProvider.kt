package com.example.carlauncher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Погода на текущий момент. */
data class Weather(
    val tempC: Int,
    val code: Int,
    val isDay: Boolean,
    val valid: Boolean = true
) {
    /** Короткое описание по коду WMO. */
    val description: String
        get() = when (code) {
            0 -> "Ясно"
            1, 2 -> "Малооблачно"
            3 -> "Пасмурно"
            45, 48 -> "Туман"
            51, 53, 55, 56, 57 -> "Морось"
            61, 63, 65, 66, 67 -> "Дождь"
            71, 73, 75, 77 -> "Снег"
            80, 81, 82 -> "Ливень"
            85, 86 -> "Снегопад"
            95, 96, 99 -> "Гроза"
            else -> ""
        }

    companion object {
        val EMPTY = Weather(0, 0, true, valid = false)
    }
}

/**
 * Погода через Open-Meteo.
 *
 * Выбран потому, что не требует ключа API и регистрации — важно для
 * приложения, которое ставят сайдлоадом на магнитолу. Координаты берём
 * из последней известной позиции GPS: отдельный запрос не нужен,
 * разрешение уже запрошено ради спидометра.
 */
object WeatherProvider {

    private const val CACHE_MS = 15 * 60 * 1000L   // обновлять не чаще раза в 15 минут

    private var cached: Weather = Weather.EMPTY
    private var cachedAt: Long = 0L

    /** Последняя известная позиция без ожидания фикса GPS. */
    private fun lastLocation(context: Context): Location? {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        return runCatching {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    /**
     * Забирает погоду. Возвращает кэш, если он свежий.
     * Вызывать только из фонового потока.
     */
    suspend fun fetch(context: Context, force: Boolean = false): Weather =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!force && cached.valid && now - cachedAt < CACHE_MS) return@withContext cached

            val loc = lastLocation(context) ?: return@withContext cached
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${loc.latitude}" +
                "&longitude=${loc.longitude}" +
                "&current=temperature_2m,weather_code,is_day" +
                "&timezone=auto"

            val result = runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val cur = JSONObject(body).getJSONObject("current")
                Weather(
                    tempC = Math.round(cur.getDouble("temperature_2m")).toInt(),
                    code = cur.optInt("weather_code", 0),
                    isDay = cur.optInt("is_day", 1) == 1
                )
            }.getOrNull()

            if (result != null) {
                cached = result
                cachedAt = now
            }
            cached
        }
}

/**
 * Погода для Compose. Молча остаётся пустой, если нет сети или
 * разрешения на геолокацию — статус-бар просто не покажет температуру.
 */
@Composable
fun rememberWeather(refreshKey: Int = 0): State<Weather> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(WeatherProvider.let { Weather.EMPTY }) }

    LaunchedEffect(refreshKey) {
        state.value = WeatherProvider.fetch(context)
    }
    return state
}
