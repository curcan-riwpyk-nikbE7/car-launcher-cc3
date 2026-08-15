package com.example.carlauncher.data

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Громкость по источникам и приглушение под подсказки навигатора.
 *
 * Зачем: у радио, телефона по Bluetooth и навигатора разная громкость
 * записи. Радио обычно тише, навигатор наоборот кричит. Держать одну
 * общую громкость на всё — значит крутить её вручную при каждой смене
 * источника.
 */
object AudioProfiles {

    private const val TAG = "AudioProfiles"
    private const val PREFS = "car_launcher_shortcuts"

    // Ключи профилей. Значение — доля от максимума, 0..1
    private const val K_ENABLED = "vol_profiles_on"
    private const val K_RADIO = "vol_radio"
    private const val K_BT = "vol_bt"
    private const val K_MEDIA = "vol_media"

    private const val K_DUCK_ON = "duck_nav_on"
    private const val K_DUCK_LEVEL = "duck_nav_level"

    /** Что сейчас звучит. */
    enum class Source { Radio, Bluetooth, Media }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun am(context: Context) =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(K_ENABLED, false)

    fun setEnabled(context: Context, v: Boolean) {
        prefs(context).edit().putBoolean(K_ENABLED, v).apply()
    }

    private fun keyOf(s: Source) = when (s) {
        Source.Radio -> K_RADIO
        Source.Bluetooth -> K_BT
        Source.Media -> K_MEDIA
    }

    /** Сохранённая громкость источника. -1 если ещё не задавали. */
    fun levelOf(context: Context, s: Source): Float =
        prefs(context).getFloat(keyOf(s), -1f)

    fun setLevel(context: Context, s: Source, level: Float) {
        prefs(context).edit().putFloat(keyOf(s), level.coerceIn(0f, 1f)).apply()
    }

    /** Запомнить текущую громкость как профиль источника. */
    fun remember(context: Context, s: Source) {
        setLevel(context, s, MediaControl.volumeLevel(context))
    }

    /**
     * Применить громкость источника.
     *
     * Вызывается при смене того, что играет. Если для источника ничего
     * не сохранено — не трогаем: пользователь ещё не выражал желания,
     * менять громкость самовольно нельзя.
     */
    fun apply(context: Context, s: Source) {
        if (!enabled(context)) return
        val level = levelOf(context, s)
        if (level < 0f) return
        runCatching {
            val a = am(context)
            val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            a.setStreamVolume(AudioManager.STREAM_MUSIC, (level * max).toInt().coerceIn(0, max), 0)
        }
    }

    // ─────────────────────── приглушение под навигатор ───────────────────────

    fun duckEnabled(context: Context): Boolean = prefs(context).getBoolean(K_DUCK_ON, true)

    fun setDuckEnabled(context: Context, v: Boolean) {
        prefs(context).edit().putBoolean(K_DUCK_ON, v).apply()
    }

    /** До какой доли приглушать. 0.3 = треть от текущей. */
    fun duckLevel(context: Context): Float = prefs(context).getFloat(K_DUCK_LEVEL, 0.35f)

    fun setDuckLevel(context: Context, v: Float) {
        prefs(context).edit().putFloat(K_DUCK_LEVEL, v.coerceIn(0.05f, 0.9f)).apply()
    }

    private var focusRequest: Any? = null
    private var volumeBeforeDuck = -1

    /**
     * Просит систему приглушать чужой звук, пока говорит навигатор.
     *
     * Правильный способ на Android — запросить фокус типа
     * AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK: система сама пригасит музыку
     * и вернёт как было. Но на китайских ГУ навигатор часто фокус
     * не запрашивает вовсе и просто орёт поверх. Тогда работает запасной
     * путь — гасим сами по появлению звука навигатора.
     */
    fun startDucking(context: Context) {
        if (!duckEnabled(context)) return
        val a = am(context)
        val max = runCatching { a.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
            .getOrDefault(15).coerceAtLeast(1)
        val cur = runCatching { a.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        if (cur <= 0) return

        volumeBeforeDuck = cur
        val target = (cur * duckLevel(context)).toInt().coerceIn(0, max)
        runCatching { a.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

    /** Вернуть громкость после подсказки. */
    fun stopDucking(context: Context) {
        if (volumeBeforeDuck < 0) return
        runCatching {
            am(context).setStreamVolume(AudioManager.STREAM_MUSIC, volumeBeforeDuck, 0)
        }
        volumeBeforeDuck = -1
    }
}
