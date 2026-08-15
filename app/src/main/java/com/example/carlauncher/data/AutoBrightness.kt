package com.example.carlauncher.data

import android.content.Context
import java.util.Calendar

/**
 * Яркость по времени суток.
 *
 * Датчик освещённости на большинстве китайских ГУ либо отсутствует,
 * либо подключён к габаритам — то есть днём в туннеле экран не
 * пригасит, а ночью с выключенными фарами будет слепить. Время суток
 * для машины предсказуемее: восход и закат считаем по месяцу, как
 * в NightMode, без обращения к сети.
 */
object AutoBrightness {

    private const val PREFS = "car_launcher_shortcuts"
    private const val K_ON = "autobright_on"
    private const val K_DAY = "autobright_day"
    private const val K_NIGHT = "autobright_night"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(K_ON, false)

    fun setEnabled(context: Context, v: Boolean) {
        prefs(context).edit().putBoolean(K_ON, v).apply()
    }

    fun dayLevel(context: Context): Float = prefs(context).getFloat(K_DAY, 0.9f)
    fun nightLevel(context: Context): Float = prefs(context).getFloat(K_NIGHT, 0.25f)

    fun setDayLevel(context: Context, v: Float) {
        prefs(context).edit().putFloat(K_DAY, v.coerceIn(0.05f, 1f)).apply()
    }

    fun setNightLevel(context: Context, v: Float) {
        prefs(context).edit().putFloat(K_NIGHT, v.coerceIn(0.05f, 1f)).apply()
    }

    /**
     * Применяет яркость по текущему времени.
     * Ничего не делает, если выключено или нет разрешения на запись
     * системных настроек — молча, чтобы не дёргать пользователя.
     */
    fun apply(context: Context) {
        if (!enabled(context)) return
        if (!QuickControls.canWriteSettings(context)) return
        val night = NightMode.isNightNow()
        QuickControls.setBrightness(
            context,
            if (night) nightLevel(context) else dayLevel(context)
        )
    }

    /** Ночь ли сейчас — для подписи в настройках. */
    fun isNight(): Boolean = NightMode.isNightNow()
}
