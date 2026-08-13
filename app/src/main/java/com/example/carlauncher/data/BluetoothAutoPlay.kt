package com.example.carlauncher.data

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Автозапуск музыки при подключении телефона по Bluetooth.
 *
 * Почему это не Composable, как раньше: приёмник внутри экрана живёт
 * только пока лаунчер открыт. Пользователь же обычно сидит в навигаторе,
 * и подключение телефона там никто не ловил. Теперь приёмник объявлен
 * в манифесте — Android будит его сам, даже если лаунчер выгружен.
 *
 * Главная особенность китайских ГУ: штатное приложение «BT Музыка» не
 * просто окно, оно держит аудиоканал. Пока приложение не запущено,
 * A2DP-поток не поднимается и телефон играет «в никуда» — нажатие play
 * уходит в пустоту. Поэтому мы приложение запускаем, ждём, пока звук
 * пойдёт, и возвращаем пользователя обратно.
 */
class BluetoothAutoPlayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) return
        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
        if (state != BluetoothProfile.STATE_CONNECTED) return

        // Настройка живёт в SharedPreferences, читаем напрямую:
        // объект SettingsStore мог быть ещё не инициализирован,
        // если лаунчер не запускался в этой сессии.
        val enabled = runCatching {
            context.getSharedPreferences("car_launcher_shortcuts", Context.MODE_PRIVATE)
                .getBoolean("set_bt_autoplay", false)
        }.getOrDefault(false)
        if (!enabled) return

        BtMusicStarter.start(context.applicationContext)
    }
}

/** Подъём аудиоканала и запуск воспроизведения. */
object BtMusicStarter {

    private const val TAG = "BtMusicStarter"

    /** Чтобы двойное срабатывание брошкаста не запускало всё дважды. */
    @Volatile
    private var busy = false

    fun start(context: Context) {
        if (busy) return
        busy = true
        val h = Handler(Looper.getMainLooper())

        // Телефону нужно время договорить рукопожатие, иначе приложение
        // откроется раньше, чем система увидит подключённое устройство.
        h.postDelayed({
            if (MediaControl.read(context).isPlaying) {
                busy = false
                return@postDelayed
            }

            val opened = openBtMusicApp(context)

            // Даём приложению подняться и захватить аудиоканал
            h.postDelayed({
                // Возвращаем пользователя туда, где он был — на главный экран.
                // Без этого штатное приложение осталось бы поверх всего.
                if (opened) goHome(context)

                // Если музыка сама не пошла, досылаем play
                h.postDelayed({
                    if (!MediaControl.read(context).isPlaying) {
                        MediaControl.playPause(context)
                    }
                    busy = false
                }, 700)
            }, 1500)
        }, 2500)
    }

    /**
     * Запускает штатное приложение BT-музыки.
     * Пакет у каждого производителя свой, поэтому сначала список
     * известных, затем поиск по подписи под иконкой.
     */
    private fun openBtMusicApp(context: Context): Boolean {
        val candidates = listOf(
            "com.hzbhd.btmusic", "com.syu.btmusic", "com.ts.btmusic",
            "com.android.bluetooth.music", "com.hzbhd.bt", "com.syu.bt"
        )
        for (pkg in candidates) {
            if (AppRepository.launchPackage(context, pkg)) return true
        }
        val found = AppRepository.findByLabel(
            context, *AppRepository.BT_MUSIC_LABELS.toTypedArray()
        )
        if (found != null && AppRepository.launchPackage(context, found)) return true

        Log.w(TAG, "Приложение BT-музыки не найдено")
        return false
    }

    /** Свернуть текущее приложение — вернуться на главный экран. */
    private fun goHome(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
