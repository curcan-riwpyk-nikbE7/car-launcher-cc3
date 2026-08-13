package com.example.carlauncher.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Склейка движка, синтеза речи и состояния для UI.
 *
 * Живёт в MainActivity, потому что микрофон надо освобождать, когда
 * лаунчер уходит в фон: иначе мешает звонкам и навигатору.
 *
 * Все обращения к движку обёрнуты: помощник — необязательная функция,
 * и его поломка не должна мешать лаунчеру работать.
 */
class VoiceAssistant(private val context: Context) {

    var state by mutableStateOf(VoiceEngine.State.Off)
        private set

    var partial by mutableStateOf("")
        private set

    var reply by mutableStateOf("")
        private set

    var screenDimmed by mutableStateOf(false)
        private set

    /** Русского голоса в системе нет — подсказываем один раз. */
    var ttsMissing by mutableStateOf(false)
        private set

    var homeRequested by mutableStateOf(false)
        private set

    /** Помощник не смог подняться. UI показывает подсказку в настройках. */
    var unavailable by mutableStateOf(false)
        private set

    /** Идёт скачивание модели: 0..1. Null — не качаем. */
    var downloadProgress by mutableStateOf<Float?>(null)
        private set

    private var engine: VoiceEngine? = null
    private var speaker: VoiceSpeaker? = null
    private var scope: CoroutineScope? = null
    private var clearJob: Job? = null

    /** Текущая скорость — для ответа «какая скорость». */
    var speedProvider: () -> Int = { 0 }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * @param listenWhenReady сразу начать слушать команду, как только
     *   модель загрузится. Нужно при запуске с кнопки: иначе пользователю
     *   пришлось бы жать орб второй раз после подъёма движка.
     */
    fun start(scope: CoroutineScope, listenWhenReady: Boolean = false) {
        this.scope = scope
        if (!hasMicPermission()) {
            state = VoiceEngine.State.Error
            unavailable = true
            return
        }

        // Throwable, а не Exception: если нативной библиотеки Vosk нет
        // в APK, здесь прилетит NoClassDefFoundError, и без этого catch
        // лаунчер падал бы прямо при запуске.
        try {
            val sp = VoiceSpeaker(context)
            sp.onReady = { ok -> ttsMissing = !ok }
            sp.onSpeakingChanged = { speaking ->
                // Пока помощник говорит, микрофон слушать не должен —
                // иначе он распознаёт собственный голос как команду.
                if (speaking) engine?.pause() else engine?.resume()
            }
            sp.init()
            speaker = sp

            val e = VoiceEngine(context)
            e.onState = { state = it }
            e.onPartial = { partial = it }
            e.onCommand = { result -> handle(result) }
            e.onUnknown = { show("Не расслышал") }
            e.onError = { msg ->
                unavailable = true
                show(msg)
            }
            if (listenWhenReady) {
                // Движок сообщит, что перешёл в ожидание — значит модель
                // уже в памяти и можно сразу принимать команду.
                e.onReady = {
                    show("Слушаю")
                    runCatching { e.triggerManually() }
                }
            }
            engine = e
            e.start(scope)
        } catch (t: Throwable) {
            unavailable = true
            state = VoiceEngine.State.Error
        }
    }

    private fun handle(result: VoiceResult) {
        partial = ""

        when (result.action) {
            is VoiceAction.ScreenOff -> screenDimmed = true
            is VoiceAction.ScreenOn -> screenDimmed = false
            is VoiceAction.GoHome -> homeRequested = true
            else -> Unit
        }

        val spoken = runCatching {
            VoiceExecutor.execute(context, result.action, speedProvider())
        }.getOrDefault("")

        val text = spoken.ifBlank { result.reply }
        if (text.isNotBlank()) {
            show(text)
            speaker?.speak(text)
        }
    }

    private fun show(text: String) {
        reply = text
        clearJob?.cancel()
        clearJob = scope?.launch {
            delay(2600)
            reply = ""
        }
    }

    /**
     * Ручной запуск по кнопке-микрофону.
     *
     * Кнопка обязана давать отклик всегда: если промолчать, пользователь
     * решит, что лаунчер завис. Поэтому на каждую причину отказа —
     * свой понятный текст на экране.
     */
    fun listenNow() {
        if (!hasMicPermission()) {
            show("Нет доступа к микрофону")
            return
        }
        // Модели ещё нет — предлагаем скачать прямо сейчас, а не
        // отправляем пользователя копировать файлы на флешку.
        if (!ModelDownloader.isInstalled(context)) {
            downloadModel()
            return
        }
        // Модель есть, но движок не поднялся. Типичный случай: при старте
        // лаунчера модели не было, помощник выставил unavailable и больше
        // не пробовал, а скачали её потом из настроек — те живут в другом
        // процессе и сбросить флаг не могут. Поэтому пробуем поднять снова.
        val e = engine
        if (e == null || unavailable) {
            val sc = scope
            if (sc != null) {
                unavailable = false
                runCatching { engine?.release() }
                engine = null
                show("Запускаю помощника…")
                start(sc, listenWhenReady = true)
            } else {
                show("Помощник не готов")
            }
            return
        }
        show("Слушаю")
        runCatching { e.triggerManually() }
    }

    /**
     * Качает модель (нужен Wi-Fi один раз) и сразу поднимает помощника.
     * Повторные нажатия во время загрузки игнорируем.
     */
    fun downloadModel() {
        if (downloadProgress != null) return
        val sc = scope ?: return

        downloadProgress = 0f
        // Гасим таймер автоочистки: иначе через 2.6 секунды он сотрёт
        // прогресс, и загрузка на 88 МБ будет выглядеть как зависание.
        clearJob?.cancel()
        reply = "Скачиваю модель…"

        sc.launch {
            val ok = ModelDownloader.download(context) { p ->
                downloadProgress = p
                reply = "Скачиваю модель… ${(p * 100).toInt()}%"
            }
            downloadProgress = null
            if (ok) {
                show("Модель готова, запускаю помощника")
                unavailable = false
                runCatching { engine?.release() }
                engine = null
                start(sc, listenWhenReady = true)
            } else {
                show("Не удалось скачать. Нужен интернет")
            }
        }
    }

    fun wakeScreen() { screenDimmed = false }

    fun consumeHomeRequest() { homeRequested = false }

    fun openTtsSettings() { speaker?.openTtsSettings() }

    fun dismissTtsWarning() { ttsMissing = false }

    fun pause() { runCatching { engine?.pause() } }

    fun resume() {
        if (hasMicPermission()) runCatching { engine?.resume() }
    }

    fun release() {
        runCatching { engine?.release() }
        runCatching { speaker?.release() }
    }
}
