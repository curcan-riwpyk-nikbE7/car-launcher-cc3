package com.example.carlauncher.voice

import com.example.carlauncher.data.SettingsStore
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/**
 * Офлайн-распознавание речи на Vosk.
 *
 * Два режима, между которыми движок переключается сам:
 *
 *  1. ОЖИДАНИЕ — слушаем только слово активации. Распознаватель создан
 *     с грамматикой из трёх фраз, поэтому почти не грузит процессор:
 *     движок сравнивает с тремя вариантами, а не строит гипотезы по
 *     всему словарю. На слабом Unisoc это принципиально.
 *
 *  2. КОМАНДА — подставляем полную грамматику и слушаем 6 секунд.
 *     Молчание — возвращаемся в ожидание.
 *
 * ВАЖНО про надёжность: весь Vosk грузится через JNI. Если библиотеки
 * нет в сборке или процессор другой архитектуры, JVM бросает
 * UnsatisfiedLinkError / NoClassDefFoundError — это Error, а НЕ Exception.
 * Обычный catch (e: Exception) его пропускает, и лаунчер падает при
 * старте. Поэтому везде ловим Throwable.
 */
class VoiceEngine(private val context: Context) {

    enum class State { Off, Loading, Waiting, Listening, Error }

    var onState: (State) -> Unit = {}
    var onPartial: (String) -> Unit = {}
    var onCommand: (VoiceResult) -> Unit = {}
    var onUnknown: (String) -> Unit = {}
    var onError: (String) -> Unit = {}

    /** Модель загружена и движок готов принимать команды. */
    var onReady: () -> Unit = {}

    private var model: Model? = null
    private var service: SpeechService? = null
    private var mode = Mode.Wake
    private var lastActivity = 0L

    private enum class Mode { Wake, Command }

    companion object {
        private const val TAG = "VoiceEngine"

        /** Слово активации. Короткий список — меньше ложных срабатываний. */
        private val WAKE_GRAMMAR = listOf(
            "привет машина", "слушай машина", "машина"
        )

        private const val COMMAND_WINDOW_MS = 6000L

        /**
         * 16 кГц — частота, на которой обучена модель. Несовпадение —
         * самая частая причина «оно ничего не слышит».
         */
        private const val SAMPLE_RATE = 16000f

        /** Куда пользователь может положить модель вручную. */
        const val MANUAL_PATH = "/sdcard/CarLauncher/vosk-model-ru"
    }

    /**
     * Поднимает помощника. Любая ошибка здесь НЕ должна ронять лаунчер:
     * помощник — дополнительная функция, без него всё остальное работает.
     */
    fun start(scope: CoroutineScope) {
        onState(State.Loading)
        scope.launch(Dispatchers.IO) {
            try {
                val path = findModel()
                if (path == null) {
                    withContext(Dispatchers.Main) {
                        onState(State.Error)
                        onError("Модель распознавания не найдена")
                    }
                    return@launch
                }
                val m = Model(path)
                withContext(Dispatchers.Main) {
                    model = m
                    listenForWake()
                    onReady()
                }
            } catch (t: Throwable) {
                // Именно Throwable: UnsatisfiedLinkError при отсутствии
                // нативной библиотеки — это Error, а не Exception.
                Log.e(TAG, "Помощник недоступен", t)
                withContext(Dispatchers.Main) {
                    onState(State.Error)
                    onError("Помощник недоступен")
                }
            }
        }
    }

    /**
     * Где искать модель, по порядку:
     *  1. уже распакованная во внутренней памяти;
     *  2. папка, положенная пользователем на память/флешку;
     *  3. assets внутри APK — если собрана версия с зашитой моделью.
     *
     * Такой поиск нужен, потому что модель весит 88 МБ: зашивать её
     * в APK не всегда разумно, а качать в машине часто неоткуда.
     */
    private fun findModel(): String? {
        val unpacked = File(context.filesDir, "vosk-model-ru")
        if (File(unpacked, "am/final.mdl").exists()) return unpacked.absolutePath

        val candidates = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, "vosk-model-ru") },
            File(MANUAL_PATH),
            File("/storage/emulated/0/CarLauncher/vosk-model-ru")
        )
        for (dir in candidates) {
            if (File(dir, "am/final.mdl").exists()) return dir.absolutePath
        }

        val hasAssets = runCatching {
            context.assets.list("model-ru")?.isNotEmpty() == true
        }.getOrDefault(false)
        if (hasAssets) return unpackModel()

        return null
    }

    /** Распаковка из assets с кэшированием — 88 МБ каждый старт недопустимо. */
    private fun unpackModel(): String {
        val target = File(context.filesDir, "vosk-model-ru")
        val marker = File(target, ".unpacked")
        if (marker.exists()) return target.absolutePath

        if (target.exists()) target.deleteRecursively()
        target.mkdirs()
        copyAssetDir("model-ru", target)
        marker.writeText("ok")
        return target.absolutePath
    }

    private fun copyAssetDir(assetPath: String, dest: File) {
        val items = context.assets.list(assetPath) ?: return
        if (items.isEmpty()) {
            dest.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            return
        }
        dest.mkdirs()
        for (item in items) copyAssetDir("$assetPath/$item", File(dest, item))
    }

    private fun listenForWake() {
        val m = model ?: return
        stopService()
        mode = Mode.Wake

        // Слово активации выключено — микрофон не держим открытым.
        // Так он не мешает звонкам и навигатору, а помощник всё равно
        // доступен кнопкой-орбом на панели.
        if (!SettingsStore.voiceWake.value) {
            onState(State.Off)
            return
        }

        try {
            val rec = Recognizer(m, SAMPLE_RATE, jsonGrammar(WAKE_GRAMMAR))
            service = SpeechService(rec, SAMPLE_RATE).also { it.startListening(listener) }
            onState(State.Waiting)
        } catch (t: Throwable) {
            Log.e(TAG, "Микрофон недоступен", t)
            onState(State.Error)
            onError("Нет доступа к микрофону")
        }
    }

    private fun listenForCommand() {
        val m = model ?: return
        stopService()
        mode = Mode.Command
        lastActivity = System.currentTimeMillis()
        try {
            val rec = Recognizer(m, SAMPLE_RATE, jsonGrammar(VoiceCommands.grammar))
            service = SpeechService(rec, SAMPLE_RATE).also { it.startListening(listener) }
            onState(State.Listening)
        } catch (t: Throwable) {
            listenForWake()
        }
    }

    /** Ручной запуск — по кнопке, минуя слово активации. */
    fun triggerManually() {
        if (model != null) listenForCommand()
    }

    /**
     * Грамматика для Vosk — JSON-массив строк. "[unk]" обязателен:
     * без него движок подгонит любой шум под ближайшую команду,
     * и «кхм» превратится в «выключи экран».
     */
    private fun jsonGrammar(phrases: List<String>): String =
        (phrases + "[unk]").joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }

    private val listener = object : RecognitionListener {

        override fun onPartialResult(hypothesis: String?) {
            val text = extract(hypothesis, "partial")
            if (text.isNotBlank() && mode == Mode.Command) {
                lastActivity = System.currentTimeMillis()
                onPartial(text)
            }
        }

        override fun onResult(hypothesis: String?) {
            val text = extract(hypothesis, "text")
            if (text.isBlank()) {
                checkTimeout()
                return
            }
            when (mode) {
                Mode.Wake -> if (isWake(text)) listenForCommand()
                Mode.Command -> handleCommand(text)
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            val text = extract(hypothesis, "text")
            if (mode == Mode.Command && text.isNotBlank()) handleCommand(text)
        }

        override fun onError(e: Exception?) {
            Log.e(TAG, "Ошибка распознавания", e)
            listenForWake()
        }

        override fun onTimeout() {
            listenForWake()
        }
    }

    private fun handleCommand(text: String) {
        val result = VoiceCommands.parse(text)
        if (result != null) onCommand(result) else onUnknown(text)
        // После любой команды возвращаемся в ожидание, иначе помощник
        // останется висеть с включённым микрофоном.
        listenForWake()
    }

    private fun checkTimeout() {
        if (mode == Mode.Command &&
            System.currentTimeMillis() - lastActivity > COMMAND_WINDOW_MS
        ) listenForWake()
    }

    private fun isWake(text: String): Boolean {
        val t = text.lowercase().replace('ё', 'е')
        return t.contains("машин") || t.contains("привет")
    }

    private fun extract(json: String?, key: String): String = runCatching {
        JSONObject(json ?: return "").optString(key, "").replace("[unk]", "").trim()
    }.getOrDefault("")

    private fun stopService() {
        runCatching {
            service?.stop()
            service?.shutdown()
        }
        service = null
    }

    /** Микрофон освобождаем, чтобы не мешать звонкам. */
    fun pause() {
        stopService()
        onState(State.Off)
    }

    fun resume() {
        if (model != null && service == null) listenForWake()
    }

    fun release() {
        stopService()
        runCatching { model?.close() }
        model = null
        onState(State.Off)
    }
}
