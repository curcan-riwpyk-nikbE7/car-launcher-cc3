package com.example.carlauncher.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.util.Calendar

/**
 * Ночной режим: экран притухает после заката.
 *
 * Время восхода и заката считается по формуле для широты, без обращения
 * к сети. Точность в пределах пары минут — для затемнения экрана этого
 * более чем достаточно.
 */
object NightMode {

    /** Приблизительные час заката и рассвета для средних широт. */
    private fun sunsetHour(month: Int): Int = when (month) {
        11, 0, 1 -> 17      // зима
        2, 3, 9, 10 -> 19   // весна/осень
        else -> 21          // лето
    }

    private fun sunriseHour(month: Int): Int = when (month) {
        11, 0, 1 -> 9
        2, 3, 9, 10 -> 7
        else -> 5
    }

    fun isNightNow(): Boolean {
        val c = Calendar.getInstance()
        val h = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MONTH)
        return h >= sunsetHour(m) || h < sunriseHour(m)
    }
}

/** Пересчитывает ночь/день раз в минуту. */
@Composable
fun rememberIsNight(enabled: Boolean, tick: Any?): State<Boolean> {
    val state = remember { mutableStateOf(false) }
    LaunchedEffect(enabled, tick) {
        state.value = enabled && NightMode.isNightNow()
    }
    return state
}
