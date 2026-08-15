package com.example.carlauncher.data

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Настройки лаунчера. Как и ThemeStore — синглтон на mutableStateOf,
 * поэтому изменение параметра сразу перерисовывает интерфейс.
 */
object SettingsStore {

    private const val PREFS = "car_launcher_shortcuts"

    private const val K_GESTURES = "set_gestures_enabled"
    private const val K_SWIPE = "set_swipe_threshold"
    private const val K_VOLUME_STEP = "set_volume_step"
    private const val K_HAPTIC = "set_haptic"
    private const val K_KEEP_SCREEN = "set_keep_screen_on"
    private const val K_IMMERSIVE = "set_immersive"
    private const val K_SHOW_SPEED = "set_show_speed"
    private const val K_RADIO_NAME = "slot_radio_name"
    private const val K_NIGHT = "set_night_mode"
    private const val K_TRIP = "set_show_trip"
    private const val K_BT_AUTOPLAY = "set_bt_autoplay"
    private const val K_VOICE_WAKE = "set_voice_wake"
    private const val K_PREWARM = "set_prewarm_window"
    private const val K_SPEED_MODE = "set_speed_mode"
    private const val K_SPEED_AREA = "set_speed_area"

    private var prefs: android.content.SharedPreferences? = null

    /** Жесты по экрану включены. */
    val gesturesEnabled: MutableState<Boolean> = mutableStateOf(true)

    /** Порог свайпа для смены трека, dp. */
    val swipeThreshold: MutableState<Float> = mutableFloatStateOf(80f)

    /** Сколько dp нужно протянуть на один шаг громкости. */
    val volumeStep: MutableState<Float> = mutableFloatStateOf(40f)

    /** Вибро-отклик на жестах и долгих нажатиях. */
    val hapticEnabled: MutableState<Boolean> = mutableStateOf(true)

    /** Не гасить экран, пока лаунчер открыт. */
    val keepScreenOn: MutableState<Boolean> = mutableStateOf(true)

    /** Прятать системные бары. */
    val immersive: MutableState<Boolean> = mutableStateOf(true)

    /** Показывать спидометр на карточке авто. */
    val showSpeed: MutableState<Boolean> = mutableStateOf(true)

    /** Подпись радиостанции на карточке. */
    val radioName: MutableState<String> = mutableStateOf("Авто Радио")

    /** Ночной режим: притухание после заката. */
    val nightMode: MutableState<Boolean> = mutableStateOf(false)

    /** Показывать трип-компьютер на карточке авто. */
    val showTrip: MutableState<Boolean> = mutableStateOf(true)

    /**
     * Поднимать BT-приложение при подключении телефона.
     * По умолчанию включено: без этого на китайских ГУ телефон играет
     * «в никуда», пока штатное приложение не открыть руками.
     */
    val btAutoPlay: MutableState<Boolean> = mutableStateOf(true)

    /**
     * Слушать слово активации «Привет, машина».
     * По умолчанию выключено: постоянно открытый микрофон мешает
     * звонкам, а при громкой музыке всё равно срабатывает плохо.
     * Кнопка-орб на панели работает всегда.
     */
    val voiceWake: MutableState<Boolean> = mutableStateOf(false)

    /**
     * Открывать видео и карты сначала на весь экран, затем в окне.
     * Без этого YouTube и свежий Яндекс.Навигатор показывают пустое
     * окно — тем же приёмом лечила проблему сама TEYES.
     */
    val prewarmWindow: MutableState<Boolean> = mutableStateOf(true)

    /** Режим карточки спидометра: embed | freeform | split | full. */
    val speedMode: MutableState<String> = mutableStateOf("embed")


    /** Область плавающего окна: Card | RightColumn | RightHalf. */
    val speedArea: MutableState<String> = mutableStateOf("RightColumn")

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        gesturesEnabled.value = p.getBoolean(K_GESTURES, true)
        swipeThreshold.value = p.getFloat(K_SWIPE, 80f)
        volumeStep.value = p.getFloat(K_VOLUME_STEP, 40f)
        hapticEnabled.value = p.getBoolean(K_HAPTIC, true)
        keepScreenOn.value = p.getBoolean(K_KEEP_SCREEN, true)
        immersive.value = p.getBoolean(K_IMMERSIVE, true)
        showSpeed.value = p.getBoolean(K_SHOW_SPEED, true)
        radioName.value = p.getString(K_RADIO_NAME, "Авто Радио") ?: "Авто Радио"
        nightMode.value = p.getBoolean(K_NIGHT, false)
        showTrip.value = p.getBoolean(K_TRIP, true)
        btAutoPlay.value = p.getBoolean(K_BT_AUTOPLAY, true)
        voiceWake.value = p.getBoolean(K_VOICE_WAKE, false)
        prewarmWindow.value = p.getBoolean(K_PREWARM, true)
        // Умный дефолт: встраивание предлагаем только там, где оно реально
        // заработает (системная сборка на прошивке с ключом AOSP).
        // На обычной сборке сразу freeform — иначе пользователь при первом
        // запуске упирается в чёрный прямоугольник.
        val defaultMode =
            if (SystemPrivileges.canEmbedActivities(context)) "embed" else "freeform"
        speedMode.value = p.getString(K_SPEED_MODE, defaultMode) ?: defaultMode
        speedArea.value = p.getString(K_SPEED_AREA, "RightColumn") ?: "RightColumn"
    }

    fun setGestures(v: Boolean) { gesturesEnabled.value = v; prefs?.edit()?.putBoolean(K_GESTURES, v)?.apply() }
    fun setSwipe(v: Float) { swipeThreshold.value = v; prefs?.edit()?.putFloat(K_SWIPE, v)?.apply() }
    fun setVolumeStep(v: Float) { volumeStep.value = v; prefs?.edit()?.putFloat(K_VOLUME_STEP, v)?.apply() }
    fun setHaptic(v: Boolean) { hapticEnabled.value = v; prefs?.edit()?.putBoolean(K_HAPTIC, v)?.apply() }
    fun setKeepScreenOn(v: Boolean) { keepScreenOn.value = v; prefs?.edit()?.putBoolean(K_KEEP_SCREEN, v)?.apply() }
    fun setImmersive(v: Boolean) { immersive.value = v; prefs?.edit()?.putBoolean(K_IMMERSIVE, v)?.apply() }
    fun setShowSpeed(v: Boolean) { showSpeed.value = v; prefs?.edit()?.putBoolean(K_SHOW_SPEED, v)?.apply() }
    fun setNightMode(v: Boolean) { nightMode.value = v; prefs?.edit()?.putBoolean(K_NIGHT, v)?.apply() }
    fun setShowTrip(v: Boolean) { showTrip.value = v; prefs?.edit()?.putBoolean(K_TRIP, v)?.apply() }
    fun setBtAutoPlay(v: Boolean) { btAutoPlay.value = v; prefs?.edit()?.putBoolean(K_BT_AUTOPLAY, v)?.apply() }
    fun setVoiceWake(v: Boolean) { voiceWake.value = v; prefs?.edit()?.putBoolean(K_VOICE_WAKE, v)?.apply() }
    fun setPrewarm(v: Boolean) { prewarmWindow.value = v; prefs?.edit()?.putBoolean(K_PREWARM, v)?.apply() }
    fun setSpeedArea(v: String) { speedArea.value = v; prefs?.edit()?.putString(K_SPEED_AREA, v)?.apply() }
    fun setSpeedMode(v: String) { speedMode.value = v; prefs?.edit()?.putString(K_SPEED_MODE, v)?.apply() }
    fun setRadioName(v: String) { radioName.value = v; prefs?.edit()?.putString(K_RADIO_NAME, v)?.apply() }

    /** Сброс всех настроек лаунчера, включая ярлыки и тему. */
    fun resetAll(context: Context) {
        prefs?.edit()?.clear()?.apply()
        init(context)
        ThemeStore.init(context)
    }
}
