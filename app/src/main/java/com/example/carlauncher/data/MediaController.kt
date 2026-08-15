package com.example.carlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController.TransportControls
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Пустой listener — нужен только чтобы система выдала доступ к MediaSession. */
class MediaNotificationListener : NotificationListenerService()

data class NowPlaying(
    val title: String = "Нет воспроизведения",
    val artist: String = "Откройте плеер",
    val isPlaying: Boolean = false,
    val artwork: Bitmap? = null,
    val hasSession: Boolean = false,
    /** Длительность трека, мс. 0 — плеер её не сообщает. */
    val durationMs: Long = 0L,
    /** Позиция на момент чтения состояния, мс. */
    val positionMs: Long = 0L,
    /** Когда была снята позиция — нужно, чтобы докручивать её локально. */
    val positionAt: Long = 0L,
    /**
     * Играет телефон по Bluetooth, а не приложение на самой магнитоле.
     * В этом случае обложки нет и метаданные приходят по AVRCP —
     * поэтому рисуем карточку-телефон, как штатный лаунчер.
     */
    val isBluetooth: Boolean = false
)

object MediaControl {

    /** Активная сессия — нужна для перемотки. */
    private var activeSession: android.media.session.MediaController? = null

    /** Выдан ли доступ к уведомлениям (нужен для чтения названия трека). */
    fun hasNotificationAccess(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(context.packageName)
    }

    fun openNotificationAccessSettings(context: Context) {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        else Settings.ACTION_SETTINGS
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Читает активную медиа-сессию. Вернёт заглушку, если доступа нет. */
    fun read(context: Context): NowPlaying {
        if (!hasNotificationAccess(context)) return NowPlaying()
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(context, MediaNotificationListener::class.java)
            val sessions = msm.getActiveSessions(component)
            val controller = sessions.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull() ?: return NowPlaying()

            activeSession = controller
            val md = controller.metadata
            val st = controller.playbackState
            NowPlaying(
                title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() } ?: "Неизвестный трек",
                artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }
                    ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
                isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                artwork = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
                hasSession = true,
                durationMs = (md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
                    .coerceAtLeast(0L),
                positionMs = (st?.position ?: 0L).coerceAtLeast(0L),
                positionAt = android.os.SystemClock.elapsedRealtime(),
                isBluetooth = isBluetoothSource(controller.packageName, md)
            )
        } catch (e: Throwable) {
            NowPlaying()
        }
    }

    /**
     * Играет ли телефон по Bluetooth.
     *
     * Надёжного признака нет, поэтому смотрим на два:
     *  1) пакет сессии — системный BT-стек или штатное BT-приложение ГУ.
     *     На китайских магнитолах оно у каждого своё, отсюда список;
     *  2) запасной путь — обложки нет вообще. По AVRCP её обычно
     *     не передают, а любое локальное приложение картинку отдаёт.
     */
    private fun isBluetoothSource(pkg: String?, md: MediaMetadata?): Boolean {
        val p = pkg?.lowercase().orEmpty()
        val known = p.contains("bluetooth") || p.contains(".bt") ||
            p == "com.android.bluetooth" || p.endsWith("btmusic")
        if (known) return true

        // Признак «пусто»: нет ни обложки, ни альбома — типично для AVRCP
        val noArt = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) == null &&
            md?.getBitmap(MediaMetadata.METADATA_KEY_ART) == null
        val noAlbum = md?.getString(MediaMetadata.METADATA_KEY_ALBUM).isNullOrBlank()
        return noArt && noAlbum && md != null
    }

    /**
     * Управление через медиа-клавиши. Работает без разрешений,
     * но это лишь имитация нажатия кнопки на гарнитуре: система сама
     * решает, кому её отдать. Bluetooth-стек на китайских ГУ такие
     * события часто игнорирует — поэтому это запасной путь, а не основной.
     */
    private fun sendKey(context: Context, keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val time = android.os.SystemClock.uptimeMillis()
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }

    /**
     * Команда прямо в активную сессию.
     *
     * Именно так листается музыка с телефона: транспорт сессии уходит
     * в BT-стек командой AVRCP, минуя раздачу медиа-клавиш. Раньше мы
     * слали только клавиши, и по Bluetooth они пропадали в никуда —
     * кнопки и жесты выглядели сломанными.
     *
     * @return false если сессии нет и надо падать на клавиши
     */
    private fun withSession(context: Context, action: (TransportControls) -> Unit): Boolean {
        // Сессию перечитываем каждый раз: телефон мог переподключиться,
        // и старая ссылка указывает на уже мёртвый контроллер.
        val ctrl = runCatching {
            if (!hasNotificationAccess(context)) return false
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(context, MediaNotificationListener::class.java)
            val sessions = msm.getActiveSessions(component)
            sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull()
        }.getOrNull() ?: return false

        activeSession = ctrl
        return runCatching { action(ctrl.transportControls); true }.getOrDefault(false)
    }

    fun playPause(context: Context) {
        val done = withSession(context) { tc ->
            // Отдельные play и pause вместо одной кнопки: часть
            // BT-стеков не реализует переключатель, только явные команды.
            if (activeSession?.playbackState?.state == PlaybackState.STATE_PLAYING) tc.pause()
            else tc.play()
        }
        if (!done) sendKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun next(context: Context) {
        if (!withSession(context) { it.skipToNext() }) {
            sendKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    fun previous(context: Context) {
        if (!withSession(context) { it.skipToPrevious() }) {
            sendKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    /**
     * Перемотка. Работает только если плеер отдал сессию —
     * медиа-клавишами позицию задать нельзя.
     * @return true, если запрос ушёл плееру
     */
    fun seekTo(context: Context, positionMs: Long): Boolean {
        val ctrl = activeSession ?: run { read(context); activeSession } ?: return false
        return runCatching {
            ctrl.transportControls.seekTo(positionMs.coerceAtLeast(0L))
            true
        }.getOrDefault(false)
    }

    /** Умеет ли текущий плеер перематывать. */
    fun canSeek(): Boolean = runCatching {
        val actions = activeSession?.playbackState?.actions ?: 0L
        (actions and android.media.session.PlaybackState.ACTION_SEEK_TO) != 0L
    }.getOrDefault(false)

    // --- Громкость для жестов ---

    private fun audio(context: Context) =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Меняет громкость на один шаг.
     * Свой UI мы рисуем сами, поэтому системную шторку не дёргаем (FLAG 0).
     * @return новый уровень в диапазоне 0..1
     */
    fun stepVolume(context: Context, up: Boolean): Float {
        val am = audio(context)
        runCatching {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                0
            )
        }
        return volumeLevel(context)
    }

    /** Текущая громкость медиа-потока в диапазоне 0..1. */
    fun volumeLevel(context: Context): Float = runCatching {
        val am = audio(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }.getOrDefault(0f)
}

/** Состояние трека, обновляется при возврате на экран и по тику. */
@Composable
fun rememberNowPlaying(tick: Int): State<NowPlaying> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(NowPlaying()) }
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner, tick) {
        state.value = MediaControl.read(context)
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = MediaControl.read(context)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return state
}
