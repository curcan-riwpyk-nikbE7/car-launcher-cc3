package com.example.carlauncher.data

import android.app.Activity
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Полноэкранный режим — один на все экраны лаунчера.
 *
 * Раньше эта логика жила внутри MainActivity, и остальные экраны
 * про неё не знали. Получалось так: главный экран занимал дисплей
 * целиком, а меню приложений и настройки открывались со штатной
 * панелью головного устройства сверху и полосой навигации снизу.
 * На экране 1280×720 они вдвоём съедали около трети высоты —
 * в меню помещалось 12 иконок вместо 20, и выглядело это так,
 * будто открылось чужое приложение.
 *
 * Теперь режим задаётся из одного места и применяется одинаково
 * везде.
 */
object ImmersiveMode {

    /**
     * Прячет системные бары или возвращает их.
     *
     * @param enabled false — показать бары: пользователь мог выключить
     *        полноэкранный режим в настройках, и его выбор действует
     *        на всех экранах сразу.
     */
    fun apply(activity: Activity, enabled: Boolean) {
        val window = activity.window
        val decor = window.decorView

        if (!enabled) {
            @Suppress("DEPRECATION")
            decor.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            runCatching {
                WindowCompat.getInsetsController(window, decor)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            return
        }

        // SYSTEM_UI_FLAG_FULLSCREEN прячет саму строку состояния.
        // Без него LAYOUT_FULLSCREEN лишь разрешает рисовать под ней,
        // а панель продолжает висеть сверху.
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        // На Android 11+ старые флаги игнорируются частью прошивок —
        // дублируем через современный контроллер.
        runCatching {
            val c = WindowCompat.getInsetsController(window, decor)
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Настройка для экранов, которые открываются поверх главного.
     *
     * Вызывается в onCreate и повторно в onResume: система возвращает
     * бары после диалогов и системных окон, и без повторного вызова
     * панель ГУ выезжает обратно.
     */
    fun applyFromSettings(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        apply(activity, SettingsStore.immersive.value)
    }
}
