package com.example.carlauncher.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Голосовые ответы через системный TTS.
 *
 * Засада китайских ГУ: движок TTS формально есть, но русского голоса
 * в нём нет — setLanguage вернёт LANG_MISSING_DATA, и приложение будет
 * молча «говорить» в пустоту. Проверяем честно и, если голоса нет,
 * просто показываем ответы на экране.
 */
class VoiceSpeaker(private val context: Context) {

    private var tts: TextToSpeech? = null

    var russianAvailable: Boolean? = null
        private set

    var onReady: (Boolean) -> Unit = {}

    /** Пока говорим — микрофон глушим, иначе услышим сами себя. */
    var onSpeakingChanged: (Boolean) -> Unit = {}

    fun init() {
        // TTS на кривых прошивках умеет падать прямо в конструкторе
        runCatching {
            tts = TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    russianAvailable = false
                    onReady(false)
                    return@TextToSpeech
                }
                val res = runCatching { tts?.setLanguage(Locale("ru", "RU")) }.getOrNull()

                val ok = res == TextToSpeech.LANG_AVAILABLE ||
                    res == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                    res == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE

                russianAvailable = ok
                if (ok) {
                    // Короткие ответы в машине должны звучать бодро
                    tts?.setSpeechRate(1.05f)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = onSpeakingChanged(true)
                        override fun onDone(utteranceId: String?) = onSpeakingChanged(false)
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) = onSpeakingChanged(false)
                    })
                }
                onReady(ok)
            }
        }.onFailure {
            russianAvailable = false
            onReady(false)
        }
    }

    fun speak(text: String) {
        if (text.isBlank() || russianAvailable != true) return
        runCatching {
            // QUEUE_FLUSH: новая команда прерывает старый ответ,
            // иначе фразы копятся и помощник тараторит.
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "carlauncher-voice")
        }
    }

    /** Настройки синтеза речи, а если их нет — страница RHVoice. */
    fun openTtsSettings() {
        runCatching {
            context.startActivity(
                Intent("com.android.settings.TTS_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.github.olga_yakovleva.rhvoice.android")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun release() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}
