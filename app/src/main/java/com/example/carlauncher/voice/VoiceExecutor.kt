package com.example.carlauncher.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import com.example.carlauncher.data.AppRepository
import com.example.carlauncher.data.MediaControl
import com.example.carlauncher.data.SettingsStore
import com.example.carlauncher.data.SystemPrivileges
import kotlin.math.roundToInt

/**
 * Выполняет разобранную команду.
 *
 * Отделено от разбора: разбор — чистая логика (тестируется на JVM),
 * здесь начинается Android с разрешениями и особенностями ГУ.
 */
object VoiceExecutor {

    /** @return текст для озвучки/показа. */
    fun execute(context: Context, action: VoiceAction, speedKmh: Int = 0): String =
        when (action) {
            is VoiceAction.ScreenOff -> screenOff(context)
            is VoiceAction.ScreenOn -> "Экран включён"

            is VoiceAction.Play -> { MediaControl.playPause(context); "Играю" }
            is VoiceAction.Pause -> { MediaControl.playPause(context); "Пауза" }
            is VoiceAction.NextTrack -> { MediaControl.next(context); "Следующий" }
            is VoiceAction.PrevTrack -> { MediaControl.previous(context); "Предыдущий" }

            is VoiceAction.VolumeUp -> {
                repeat(action.steps) { MediaControl.stepVolume(context, true) }
                "Громкость ${percent(context)}"
            }
            is VoiceAction.VolumeDown -> {
                repeat(action.steps) { MediaControl.stepVolume(context, false) }
                "Громкость ${percent(context)}"
            }
            is VoiceAction.VolumeSet -> {
                setVolume(context, action.percent); "Громкость ${action.percent}"
            }
            is VoiceAction.Mute -> { setVolume(context, 0); "Звук выключен" }
            is VoiceAction.Unmute -> { setVolume(context, 40); "Звук включён" }

            is VoiceAction.OpenApp -> openApp(context, action.kind)

            is VoiceAction.GoHome -> "Главный экран"

            is VoiceAction.NightOn -> { SettingsStore.setNightMode(true); "Ночной режим" }
            is VoiceAction.NightOff -> { SettingsStore.setNightMode(false); "Дневной режим" }

            is VoiceAction.SaySpeed ->
                if (speedKmh <= 0) "Стоим" else "$speedKmh километров в час"

            is VoiceAction.Cancel -> ""
        }

    private fun percent(context: Context): Int =
        (MediaControl.volumeLevel(context) * 100).roundToInt()

    private fun setVolume(context: Context, percent: Int) {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (max * percent / 100f).roundToInt().coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
    }

    /**
     * Выключение экрана.
     *
     * Обычному приложению Android гасить экран не даёт. В системной
     * сборке дёргаем скрытый PowerManager.goToSleep, иначе UI покажет
     * чёрную заглушку — визуально то же самое.
     */
    private fun screenOff(context: Context): String {
        if (SystemPrivileges.isSystemUid) {
            val ok = runCatching {
                val pm = context.getSystemService(Context.POWER_SERVICE)
                val m = pm.javaClass.getMethod("goToSleep", Long::class.javaPrimitiveType)
                m.invoke(pm, android.os.SystemClock.uptimeMillis())
                true
            }.getOrDefault(false)
            if (ok) return "Выключаю экран"
        }
        return "Гашу подсветку"
    }

    private fun openApp(context: Context, kind: AppKind): String = when (kind) {
        AppKind.Maps -> {
            AppRepository.launchFirstAvailable(
                context, AppRepository.NAVIGATION, errorText = "Навигация не найдена"
            )
            "Открываю навигацию"
        }
        AppKind.Music -> {
            AppRepository.launchFirstAvailable(
                context, AppRepository.MUSIC, errorText = "Плеер не найден"
            )
            "Открываю музыку"
        }
        AppKind.Radio -> {
            AppRepository.launchFirstAvailable(
                context, AppRepository.RADIO, errorText = "Радио не найдено"
            )
            "Включаю радио"
        }
        AppKind.Video -> {
            // Ютуб отдельно: он есть почти везде и его просят чаще всего
            val yt = listOf(
                "com.google.android.youtube",
                "app.revanced.android.youtube",
                "com.google.android.apps.youtube.mango"
            )
            AppRepository.launchFirstAvailable(
                context, yt + AppRepository.VIDEO,
                fallback = AppRepository.galleryFallback(),
                errorText = "Видео не найдено"
            )
            "Открываю видео"
        }
        AppKind.Phone -> {
            AppRepository.launchFirstAvailable(
                context, AppRepository.PHONE,
                fallback = AppRepository.dialerFallback(),
                errorText = "Телефон не найден"
            )
            "Открываю телефон"
        }
        AppKind.Settings -> {
            runCatching {
                context.startActivity(
                    AppRepository.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            "Открываю настройки"
        }
        AppKind.Bluetooth -> {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            "Открываю блютуз"
        }
    }
}
