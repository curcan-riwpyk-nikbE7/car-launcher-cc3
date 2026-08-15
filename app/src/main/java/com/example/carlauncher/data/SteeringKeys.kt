package com.example.carlauncher.data

import android.content.Context
import android.view.KeyEvent

/**
 * Кнопки на руле.
 *
 * Как это устроено: руль подключён к магнитоле через ADC-модуль, и
 * прошивка превращает нажатия в обычные события клавиш Android. Лаунчер
 * ловит их в onKeyDown и делает то, что назначил пользователь.
 *
 * Чего сделать нельзя: перехватить кнопку, которую прошивка обрабатывает
 * сама и до приложений не доводит. Обычно это громкость и приём вызова.
 * Такие останутся за штатной системой — это не лечится без прав прошивки.
 */
object SteeringKeys {

    private const val PREFS = "car_launcher_shortcuts"
    private const val PREFIX = "steer_key_"

    /** Что можно повесить на кнопку. */
    enum class Action(val id: String, val title: String) {
        None("none", "Ничего"),
        NextTrack("next", "Следующий трек"),
        PrevTrack("prev", "Предыдущий трек"),
        PlayPause("play", "Пуск и пауза"),
        VolumeUp("vol_up", "Громче"),
        VolumeDown("vol_down", "Тише"),
        Mute("mute", "Без звука"),
        Voice("voice", "Голосовой помощник"),
        Home("home", "Главный экран"),
        Navigation("nav", "Навигатор"),
        Radio("radio", "Радио"),
        NextSource("source", "Сменить источник")
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Что назначено на код клавиши. */
    fun actionFor(context: Context, keyCode: Int): Action {
        val id = prefs(context).getString(PREFIX + keyCode, null) ?: return Action.None
        return Action.entries.firstOrNull { it.id == id } ?: Action.None
    }

    fun assign(context: Context, keyCode: Int, action: Action) {
        val e = prefs(context).edit()
        if (action == Action.None) e.remove(PREFIX + keyCode) else e.putString(PREFIX + keyCode, action.id)
        e.apply()
    }

    /** Все назначенные кнопки: код клавиши → действие. */
    fun assignments(context: Context): Map<Int, Action> {
        val out = mutableMapOf<Int, Action>()
        prefs(context).all.forEach { (k, v) ->
            if (k.startsWith(PREFIX) && v is String) {
                val code = k.removePrefix(PREFIX).toIntOrNull() ?: return@forEach
                Action.entries.firstOrNull { it.id == v }?.let { out[code] = it }
            }
        }
        return out
    }

    fun clearAll(context: Context) {
        val e = prefs(context).edit()
        prefs(context).all.keys.filter { it.startsWith(PREFIX) }.forEach { e.remove(it) }
        e.apply()
    }

    /**
     * Человеческое имя клавиши — чтобы в настройках было видно, что
     * именно нажали, а не голый номер.
     */
    fun keyName(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> "Трек вперёд"
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Трек назад"
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Плей/пауза"
        KeyEvent.KEYCODE_VOLUME_UP -> "Громкость +"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "Громкость −"
        KeyEvent.KEYCODE_CALL -> "Вызов"
        KeyEvent.KEYCODE_ENDCALL -> "Отбой"
        KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST -> "Голос"
        KeyEvent.KEYCODE_SEARCH -> "Поиск"
        KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> "Источник"
        KeyEvent.KEYCODE_DPAD_UP -> "Вверх"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Вниз"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Влево"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Вправо"
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "ОК"
        else -> "Клавиша $keyCode"
    }

    /**
     * Выполняет назначенное действие.
     * @return true если кнопка наша и событие дальше пускать не надо
     */
    fun handle(
        context: Context,
        keyCode: Int,
        onVoice: () -> Unit,
        onHome: () -> Unit
    ): Boolean {
        return when (actionFor(context, keyCode)) {
            Action.None -> false
            Action.NextTrack -> { MediaControl.next(context); true }
            Action.PrevTrack -> { MediaControl.previous(context); true }
            Action.PlayPause -> { MediaControl.playPause(context); true }
            Action.VolumeUp -> { MediaControl.stepVolume(context, true); true }
            Action.VolumeDown -> { MediaControl.stepVolume(context, false); true }
            Action.Mute -> { QuickControls.toggleMute(context); true }
            Action.Voice -> { onVoice(); true }
            Action.Home -> { onHome(); true }
            Action.Navigation -> {
                AppRepository.launchFirstAvailable(context, AppRepository.NAVIGATION); true
            }
            Action.Radio -> {
                AppRepository.launchFirstAvailable(context, AppRepository.RADIO); true
            }
            Action.NextSource -> {
                // Простое переключение: если играет — на радио, иначе на BT.
                // Полноценной шины источников у нас нет, а так хотя бы
                // предсказуемо.
                if (MediaControl.read(context).isPlaying) {
                    AppRepository.launchFirstAvailable(context, AppRepository.RADIO)
                } else {
                    BtMusicStarter.ensureChannel(context)
                }
                true
            }
        }
    }
}
