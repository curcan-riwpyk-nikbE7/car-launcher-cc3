package com.example.carlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.carlauncher.data.AppInfo
import com.example.carlauncher.data.AppRepository
import com.example.carlauncher.data.rememberSpeedKmh
import com.example.carlauncher.data.SettingsStore
import com.example.carlauncher.data.SystemPrivileges
import com.example.carlauncher.data.TripComputer
import com.example.carlauncher.data.WallpaperStore
import com.example.carlauncher.data.ThemeStore
import com.example.carlauncher.ui.CarLauncherTheme
import com.example.carlauncher.ui.themeById
import com.example.carlauncher.ui.HomeScreen
import com.example.carlauncher.ui.VoiceOverlay
import com.example.carlauncher.ui.ScreenDimOverlay
import com.example.carlauncher.voice.VoiceAssistant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var apps by mutableStateOf<List<AppInfo>>(emptyList())

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* отказ не критичен: спидометр просто покажет 0 */ }

    /** Голосовой помощник. Необязательная функция: если не поднимется —
     *  лаунчер продолжает работать как обычно. */
    private val assistant by lazy { VoiceAssistant(this) }

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) runCatching { assistant.start(lifecycleScope) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Рисуем под системными барами — лаунчер занимает весь экран
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeStore.init(this)
        SettingsStore.init(this)
        TripComputer.init(this)
        com.example.carlauncher.data.Maintenance.init(this)
        // Тихий старт: вечером слушал громко — утром завёл и получил
        // это же в лицо. Ограничиваем только превышение потолка.
        runCatching { com.example.carlauncher.data.AudioProfiles.applySoftStart(this) }
        WallpaperStore.init(this)
        // В системной сборке разово включаем force_resizable_activities:
        // без него Карты и YouTube внутри карточки верстаются как на
        // полном экране и половина интерфейса уезжает за край.
        SystemPrivileges.enableForceResizable(this)
        enableImmersiveMode()
        requestLocationIfNeeded()

        setContent {
            val themeId by ThemeStore.current
            CarLauncherTheme(themeById(themeId)) {
                val speed by rememberSpeedKmh()
                // Помощник должен знать скорость для ответа «какая скорость»
                assistant.speedProvider = { speed }

                androidx.compose.foundation.layout.Box {
                    HomeScreen(
                        apps = apps,
                        speedKmh = speed,
                        // Орб на панели запускает наш помощник напрямую,
                        // не дожидаясь слова активации: за рулём при
                        // громкой музыке это единственный надёжный способ.
                        onVoice = { assistant.listenNow() },
                        onScreenOff = { dimScreen(true) }
                    )

                    VoiceOverlay(
                        state = assistant.state,
                        partial = assistant.partial,
                        reply = assistant.reply,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    ScreenDimOverlay(
                        visible = assistant.screenDimmed,
                        onWake = { dimScreen(false) }
                    )
                }
            }
        }
        reloadApps()
        startVoiceIfPossible()
    }

    /**
     * Поднимает помощника. Обёрнуто в runCatching: отсутствие библиотек
     * Vosk или микрофона не должно мешать запуску лаунчера.
     */
    private fun startVoiceIfPossible() {
        runCatching {
            if (assistant.hasMicPermission()) {
                assistant.start(lifecycleScope)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reloadApps()
        // Микрофон отдаём другим приложениям, пока лаунчер в фоне
        runCatching { assistant.resume() }
        // Яркость по времени суток — на случай, если магнитолу завели
        // вечером, а последний раз пользовались днём
        runCatching { com.example.carlauncher.data.AutoBrightness.apply(this) }
    }

    /**
     * Кнопки на руле.
     *
     * Руль подключён через ADC-модуль, прошивка превращает нажатия
     * в обычные события клавиш. Ловим их здесь и делаем то, что назначил
     * пользователь.
     *
     * Часть кнопок прошивка обрабатывает сама и до приложений не доводит —
     * обычно громкость и приём вызова. Такие сюда просто не придут,
     * и повлиять на это без прав прошивки нельзя.
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        // Повтор при удержании пропускаем, кроме громкости: её как раз
        // удобно крутить длинным нажатием
        val isRepeat = (event?.repeatCount ?: 0) > 0
        val action = com.example.carlauncher.data.SteeringKeys.actionFor(this, keyCode)
        val allowRepeat = action == com.example.carlauncher.data.SteeringKeys.Action.VolumeUp ||
            action == com.example.carlauncher.data.SteeringKeys.Action.VolumeDown
        if (isRepeat && !allowRepeat) return true

        val handled = com.example.carlauncher.data.SteeringKeys.handle(
            context = this,
            keyCode = keyCode,
            onVoice = { runCatching { assistant.listenNow() } },
            onHome = { /* мы и есть главный экран */ }
        )
        return if (handled) true else super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        runCatching { assistant.pause() }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { assistant.release() }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /** Системная «Назад» на домашнем экране не должна выходить из лаунчера. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // no-op
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    private fun requestLocationIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
        }
    }

    private fun reloadApps() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { AppRepository.loadApps(this@MainActivity) }
            apps = loaded
            // Заполняем пустые ярлыки тем, что реально установлено.
            // Только при первом запуске — дальше решает пользователь.
            runCatching {
                com.example.carlauncher.data.ShortcutStore(this@MainActivity)
                    .seedDefaults(this@MainActivity, loaded.map { it.packageName })
            }
        }
    }

    /**
     * Настоящее гашение экрана: яркость окна в ноль.
     *
     * Обычное приложение не может выключить дисплей — для этого нужны
     * права прошивки. Зато яркость СВОЕГО окна менять разрешено всем,
     * и на уровне драйвера это то же самое: подсветка гаснет физически,
     * а не закрашивается чёрным поверх. Чёрный слой сверху всё равно
     * нужен — на некоторых ГУ минимум яркости не нулевой.
     *
     * Флаг screenDimmed живёт в помощнике, чтобы голосовая команда
     * «выключи экран» и кнопка в шторке гасили одно и то же.
     */
    private fun dimScreen(on: Boolean) {
        assistant.screenDimmed = on
        runCatching {
            window.attributes = window.attributes.apply {
                screenBrightness = if (on) 0f
                else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    private fun enableImmersiveMode() {
        if (SettingsStore.keepScreenOn.value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (!SettingsStore.immersive.value) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            runCatching {
                androidx.core.view.WindowCompat
                    .getInsetsController(window, window.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
            return
        }
        // SYSTEM_UI_FLAG_FULLSCREEN прячет саму строку состояния.
        // Без него LAYOUT_FULLSCREEN лишь разрешает рисовать под ней,
        // а системная строка Android продолжает висеть сверху.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
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
            val c = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
