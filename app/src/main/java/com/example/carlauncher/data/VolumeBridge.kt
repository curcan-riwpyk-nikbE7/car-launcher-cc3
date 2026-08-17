package com.example.carlauncher.data

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent

/**
 * Управление громкостью на головных устройствах, где Android до неё
 * не дотягивается.
 *
 * Проблема, из-за которой файл появился. На этой магнитоле жест
 * громкости показывал полосу на экране, значение в AudioManager
 * послушно менялось, а звук оставался прежним. Штатная крутилка при
 * этом работала.
 *
 * Объяснение простое: звук идёт через внешний усилитель, которым
 * управляет MCU, а не процессор. Android держит собственный уровень,
 * ни к чему не подключённый — крутить его можно сколько угодно.
 * Физическая кнопка шлёт сигнал в железо мимо Android, поэтому
 * и работает.
 *
 * Готового API для такого случая нет: способ зависит от прошивки.
 * Поэтому здесь цепочка попыток от самой обычной к самой экзотической.
 * Первый сработавший запоминается, дальше используется только он —
 * перебирать все пять на каждый шаг громкости было бы расточительно.
 *
 * Проверить успех изнутри нельзя: getStreamVolume вернёт изменившееся
 * значение даже когда усилитель его проигнорировал. Поэтому рабочий
 * способ выбирает человек — в экране диагностики, на слух.
 */
object VolumeBridge {

    private const val TAG = "VolumeBridge"
    private const val PREFS = "car_launcher_shortcuts"
    private const val KEY_METHOD = "volume_method"

    /** Способ доставки команды до усилителя. */
    enum class Method(val id: String, val title: String, val hint: String) {
        Auto("auto", "Автоматически", "перебирать по очереди"),
        Stream("stream", "Обычный", "AudioManager — стандартный путь Android"),
        MediaKey("mediakey", "Кнопка громкости", "имитация физической клавиши"),
        Reglink("reglink", "Сервис Reglink", "broadcast прямо в службу магнитолы"),
        AudioSystem("audiosystem", "AudioSystem", "скрытый API, нужны системные права")
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Выбранный способ. Auto — пока пользователь не определился. */
    fun method(context: Context): Method {
        val id = prefs(context).getString(KEY_METHOD, Method.Auto.id)
        return Method.entries.firstOrNull { it.id == id } ?: Method.Auto
    }

    fun setMethod(context: Context, m: Method) {
        prefs(context).edit().putString(KEY_METHOD, m.id).apply()
    }

    private fun am(context: Context) =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Заблокирована ли громкость на уровне системы.
     *
     * isVolumeFixed возвращает true на устройствах, где звук выводится
     * через внешний тракт. Это прямой признак нашей ситуации, и его
     * стоит показать в диагностике: сразу видно, почему обычный путь
     * не работает.
     */
    fun isFixed(context: Context): Boolean = runCatching {
        am(context).isVolumeFixed
    }.getOrDefault(false)

    /**
     * Меняет громкость на шаг выбранным способом.
     * В режиме Auto пробует все по очереди.
     */
    fun step(context: Context, up: Boolean) {
        when (val m = method(context)) {
            Method.Auto -> {
                // Порядок не случаен: сначала штатный путь, он работает
                // на большинстве устройств и не имеет побочных эффектов.
                // Экзотика идёт следом, чтобы не дёргать чужие сервисы
                // без необходимости.
                viaStream(context, up)
                viaMediaKey(context, up)
                viaReglink(context, up)
            }
            Method.Stream -> viaStream(context, up)
            Method.MediaKey -> viaMediaKey(context, up)
            Method.Reglink -> viaReglink(context, up)
            Method.AudioSystem -> viaAudioSystem(context, up)
        }
    }

    /** Пробует конкретный способ — для проверки в диагностике. */
    fun test(context: Context, m: Method, up: Boolean): Boolean = when (m) {
        Method.Stream -> viaStream(context, up)
        Method.MediaKey -> viaMediaKey(context, up)
        Method.Reglink -> viaReglink(context, up)
        Method.AudioSystem -> viaAudioSystem(context, up)
        Method.Auto -> false
    }

    // ─────────────────────── способ 1: AudioManager ───────────────────────

    /**
     * Стандартный путь. Сначала adjustStreamVolume, затем прямая
     * установка значения: вендорский аудиосервис часто глотает первый
     * вызов молча — ни исключения, ни эффекта.
     */
    private fun viaStream(context: Context, up: Boolean): Boolean = runCatching {
        val a = am(context)
        val stream = AudioManager.STREAM_MUSIC
        val max = a.getStreamMaxVolume(stream).coerceAtLeast(1)
        val before = a.getStreamVolume(stream)

        a.adjustStreamVolume(
            stream,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0
        )
        if (a.getStreamVolume(stream) == before) {
            val target = (before + if (up) 1 else -1).coerceIn(0, max)
            if (target != before) a.setStreamVolume(stream, target, 0)
        }
        true
    }.getOrDefault(false)

    // ────────────────────── способ 2: медиа-клавиша ──────────────────────

    /**
     * Имитация физической кнопки громкости.
     *
     * Отправляем то же событие, что рождает крутилка на панели. Дальше
     * система обрабатывает его штатным маршрутом — тем самым, который
     * на этом ГУ работает. Из всех обходных путей самый вероятный.
     *
     * Нужны обе фазы, нажатие и отпускание: обработчики, ждущие ACTION_UP,
     * на одиночный DOWN не реагируют.
     */
    private fun viaMediaKey(context: Context, up: Boolean): Boolean = runCatching {
        val code = if (up) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN
        val a = am(context)
        a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        true
    }.getOrDefault(false)

    // ─────────────────────── способ 3: сервис Reglink ───────────────────────

    /**
     * Прямой канал в службу магнитолы.
     *
     * Действия найдены в образе прошивки: com.reglink.action.KEY_EVENT
     * и SIMULATE_RAW_KEY. Тем же путём до MCU доходят нажатия кнопок
     * на руле, так что канал заведомо живой.
     *
     * Имя поля с кодом клавиши неизвестно, поэтому кладём его сразу
     * под несколькими ключами — лишние служба просто не заметит.
     */
    private fun viaReglink(context: Context, up: Boolean): Boolean = runCatching {
        val code = if (up) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN
        for (action in listOf(
            "com.reglink.action.KEY_EVENT",
            "com.reglink.action.SIMULATE_RAW_KEY"
        )) {
            val i = Intent(action).apply {
                putExtra("keycode", code)
                putExtra("keyCode", code)
                putExtra("key", code)
                putExtra("value", code)
                setPackage("com.reglink.droidcarservice")
            }
            context.sendBroadcast(i)
            // Второй раз без адресата: имя пакета службы может отличаться,
            // а широковещательная рассылка дойдёт до любого слушателя.
            context.sendBroadcast(Intent(action).apply {
                putExtra("keycode", code)
                putExtra("keyCode", code)
            })
        }
        true
    }.getOrDefault(false)

    // ───────────────────── способ 4: AudioSystem ─────────────────────

    /**
     * Скрытый API уровнем ниже AudioManager.
     *
     * Иногда проходит там, где обычный путь блокируется, но требует
     * системных прав — работает только в сборке, подписанной ключом
     * платформы.
     */
    private fun viaAudioSystem(context: Context, up: Boolean): Boolean = runCatching {
        val a = am(context)
        val stream = AudioManager.STREAM_MUSIC
        val max = a.getStreamMaxVolume(stream).coerceAtLeast(1)
        val target = (a.getStreamVolume(stream) + if (up) 1 else -1).coerceIn(0, max)

        val cls = Class.forName("android.media.AudioSystem")
        val m = cls.getMethod(
            "setStreamVolumeIndex",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        // DEVICE_OUT_SPEAKER = 2: встроенный динамик, к которому
        // на магнитоле подключён усилитель.
        m.invoke(null, stream, target, 2)
        true
    }.getOrElse {
        Log.w(TAG, "AudioSystem недоступен", it)
        false
    }
}
