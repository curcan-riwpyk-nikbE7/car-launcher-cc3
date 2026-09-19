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
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import com.example.carlauncher.ui.WidgetPickerDialog
import com.example.carlauncher.data.AppWidgetHostManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var apps by mutableStateOf<List<AppInfo>>(emptyList())
    private var pendingWidgetId = -1

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

    private val pickWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val widgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (widgetId > 0) {
                checkWidgetConfigure(widgetId)
            }
        } else if (pendingWidgetId > 0) {
            AppWidgetHostManager.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
        }
    }

    private val configureWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (pendingWidgetId > 0) {
            SettingsStore.setCardWidgetId(pendingWidgetId)
            SettingsStore.setCardContentMode(SettingsStore.CARD_MODE_WIDGET)
            pendingWidgetId = -1
        }
    }

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            if (pendingWidgetId > 0) {
                checkWidgetConfigure(pendingWidgetId)
            }
        } else if (pendingWidgetId > 0) {
            AppWidgetHostManager.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
        }
    }

    private fun checkWidgetConfigure(widgetId: Int) {
        val info = AppWidgetHostManager.getAppWidgetInfo(widgetId)
        if (info?.configure != null) {
            pendingWidgetId = widgetId
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            try {
                configureWidgetLauncher.launch(intent)
            } catch (e: Exception) {
                Log.w("MainActivity", "Configure activity failed or not exported: ${e.message}")
                SettingsStore.setCardWidgetId(widgetId)
                SettingsStore.setCardContentMode(SettingsStore.CARD_MODE_WIDGET)
                pendingWidgetId = -1
            }
        } else {
            SettingsStore.setCardWidgetId(widgetId)
            SettingsStore.setCardContentMode(SettingsStore.CARD_MODE_WIDGET)
        }
    }

    private var isCustomWidgetPickerOpen by mutableStateOf(false)

    private fun handleWidgetSelected(info: AppWidgetProviderInfo) {
        runCatching {
            val widgetId = AppWidgetHostManager.allocateAppWidgetId()
            if (widgetId <= 0) return
            val wm = AppWidgetManager.getInstance(this)

            var success = false
            try {
                success = wm.bindAppWidgetIdIfAllowed(widgetId, info.provider)
            } catch (e: Exception) {
                Log.w("MainActivity", "bindAppWidgetIdIfAllowed error: ${e.message}")
            }

            if (!success) {
                // Пробуем предоставить BIND_APPWIDGET через shell/root на китайском ГУ
                runCatching {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "appops set $packageName BIND_APPWIDGET allow")).waitFor()
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "appops set $packageName BIND_APPWIDGET allow")).waitFor()
                }
                try {
                    success = wm.bindAppWidgetIdIfAllowed(widgetId, info.provider)
                } catch (e: Exception) { }
            }

            if (success) {
                checkWidgetConfigure(widgetId)
            } else {
                pendingWidgetId = widgetId
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                }
                try {
                    bindWidgetLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "ACTION_APPWIDGET_BIND not available on this ROM", e)
                    Toast.makeText(
                        this,
                        "Магнитола заблокировала виджет (прошивка запретила привязку)",
                        Toast.LENGTH_LONG
                    ).show()
                    AppWidgetHostManager.deleteAppWidgetId(widgetId)
                    pendingWidgetId = -1
                }
            }
        }.onFailure { err ->
            Log.e("MainActivity", "handleWidgetSelected failed: ${err.message}", err)
            Toast.makeText(this, "Не удалось добавить виджет: ${err.message}", Toast.LENGTH_SHORT).show()
            pendingWidgetId = -1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Рисуем под системными барами — лаунчер занимает весь экран
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeStore.init(this)
        SettingsStore.init(this)
        AppWidgetHostManager.init(this)
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
                        onScreenOff = { dimScreen(true) },
                        onPickWidget = { isCustomWidgetPickerOpen = true }
                    )

                    if (isCustomWidgetPickerOpen) {
                        WidgetPickerDialog(
                            onSelectWidget = { info: AppWidgetProviderInfo ->
                                isCustomWidgetPickerOpen = false
                                handleWidgetSelected(info)
                            },
                            onDismiss = { isCustomWidgetPickerOpen = false }
                        )
                    }

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

    override fun onStart() {
        super.onStart()
        AppWidgetHostManager.startListening()
    }

    override fun onStop() {
        super.onStop()
        AppWidgetHostManager.stopListening()
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

    /**
     * Полноэкранный режим и подсветка экрана.
     *
     * Сама работа с барами вынесена в ImmersiveMode: тот же режим
     * нужен меню приложений и настройкам, а раньше эта логика жила
     * только здесь — и остальные экраны открывались с панелью ГУ.
     */
    private fun enableImmersiveMode() {
        if (SettingsStore.keepScreenOn.value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        com.example.carlauncher.data.ImmersiveMode.apply(this, SettingsStore.immersive.value)
    }
}
