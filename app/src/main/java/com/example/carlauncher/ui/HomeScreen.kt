package com.example.carlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.AllAppsActivity
import com.example.carlauncher.SettingsActivity
import com.example.carlauncher.data.AppInfo
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import com.example.carlauncher.data.AppRepository
import com.example.carlauncher.data.DefaultLauncherCheck
import com.example.carlauncher.data.PackageChangeEffect
import com.example.carlauncher.data.MediaControl
import com.example.carlauncher.data.BtMusicStarter
import com.example.carlauncher.data.SystemPrivileges
import com.example.carlauncher.data.SettingsStore
import com.example.carlauncher.data.FreeformLauncher
import com.example.carlauncher.data.SplitScreen
import androidx.compose.ui.graphics.asImageBitmap
import com.example.carlauncher.data.TripComputer
import com.example.carlauncher.data.rememberWeather
import com.example.carlauncher.data.WallpaperStore
import com.example.carlauncher.data.rememberIsNight
import com.example.carlauncher.data.ShortcutStore
import com.example.carlauncher.data.TaskMover
import com.example.carlauncher.data.rememberNowPlaying
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    apps: List<AppInfo>,
    speedKmh: Int = 0,
    /**
     * Нажатие на орб-микрофон. По умолчанию зовём системный помощник —
     * так экран остаётся рабочим в превью и если наш движок не поднялся.
     */
    onVoice: (() -> Unit)? = null,
    /** Гашение подсветки держит Activity: из Compose до Window не дотянуться. */
    onScreenOff: (() -> Unit)? = null,
    /** Вызов системного пикера виджетов Android (Яндекс Музыка и др.). */
    onPickWidget: () -> Unit = {}
) {
    val context = LocalContext.current
    val spec = LocalThemeSpec.current
    val store = remember { ShortcutStore(context) }
    var revision by remember { mutableStateOf(0) }
    var pickerSlot by remember { mutableStateOf<String?>(null) }
    var pickerTitle by remember { mutableStateOf("") }
    var modeDialogOpen by remember { mutableStateOf(false) }

    val nowPlaying by rememberNowPlaying(revision)
    // Имя станции для карточек радио: если играет радио-приложение
    // и его сессия отдала название — берём его; иначе подпись
    // радио-приложения из настроек. Частоту радио ГУ не сообщает,
    // поэтому везде рисуем станцию, а не выдуманную «87.50».
    val radioStationName = nowPlaying.pkg
        ?.takeIf { AppRepository.RADIO.contains(it) }
        ?.let { nowPlaying.title }
        ?.takeIf { it.isNotBlank() && it != "Неизвестный трек" }
        ?: SettingsStore.radioName.value
    val feedback = rememberGestureFeedback()

    // Часы для панели
    var now by remember { mutableStateOf(Date()) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { now = Date() }
        }
        context.registerReceiver(r, IntentFilter(Intent.ACTION_TIME_TICK))
        onDispose { runCatching { context.unregisterReceiver(r) } }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Ключ обновления погоды: меняется раз в 15 минут по тику часов,
    // сам провайдер дополнительно кэширует ответ.
    val weatherKey = remember(now) { (now.time / (15 * 60 * 1000L)).toInt() }

    val favSlots = listOf(
        ShortcutStore.SLOT_1, ShortcutStore.SLOT_2, ShortcutStore.SLOT_3,
        ShortcutStore.SLOT_4, ShortcutStore.SLOT_5, ShortcutStore.SLOT_6,
        ShortcutStore.SLOT_7, ShortcutStore.SLOT_8, ShortcutStore.SLOT_9,
        ShortcutStore.SLOT_10
    )
    val favorites = remember(revision, apps) {
        favSlots.map { slot ->
            store.get(slot)?.let { key ->
                when (key) {
                    "builtin:all_apps" -> AppInfo("Все приложения", "builtin:all_apps", "all_apps", null)
                    "builtin:settings" -> AppInfo("Настройки", "builtin:settings", "settings", null)
                    else -> apps.firstOrNull { it.packageName == key }
                }
            }
        }
    }

    // Приложение, назначенное на виджет спидометра
    val speedApp = remember(revision, apps) {
        store.get(ShortcutStore.SLOT_SPEED)?.let { pkg ->
            apps.firstOrNull { it.packageName == pkg }
        }
    }

    // Приложение, назначенное на окно навигации
    val navApp = remember(revision, apps) {
        store.get(ShortcutStore.SLOT_NAV)?.let { pkg ->
            apps.firstOrNull { it.packageName == pkg }
        }
    }

    var playerExpanded by remember { mutableStateOf(false) }
    var radioExpanded by remember { mutableStateOf(false) }
    var carExpanded by remember { mutableStateOf(false) }
    var carInfoOpen by remember { mutableStateOf(false) }
    var shadeOpen by remember { mutableStateOf(false) }
    val isNight by rememberIsNight(SettingsStore.nightMode.value, now)

    // Баннер «назначьте лаунчер». Проверяем при каждом появлении экрана
    // и прячем навсегда, если пользователь его закрыл.
    var bannerDismissed by remember { mutableStateOf(false) }
    var isDefaultLauncher by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultLauncher = DefaultLauncherCheck.isDefault(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Список приложений обновляется сам при установке/удалении
    PackageChangeEffect { revision++ }
    // Приложение, встроенное в карточку прямо сейчас
    var embedFailed by remember { mutableStateOf(false) }
    // Что показывает карточка: спидометр или встроенное приложение.
    // Тап по спидометру переключает на приложение, кнопка «спидометр»
    // под ним возвращает обратно — как в штатных лаунчерах. Режим
    // хранится в SettingsStore и переживает перезапуск лаунчера:
    // заглушил машину с картой — снова сел, карточка так и с картой.

    // Звук при превышении лимита скорости. Сигналим один раз в момент
    // входа в «зону превышения», а не гудим всё время, пока едем быстро.
    var overLimitBeeped by remember { mutableStateOf(false) }
    LaunchedEffect(
        speedKmh,
        SettingsStore.speedLimitEnabled.value,
        SettingsStore.speedLimitKmh.value
    ) {
        val over = SettingsStore.speedLimitEnabled.value &&
            speedKmh >= SettingsStore.speedLimitKmh.value
        if (over && !overLimitBeeped && SettingsStore.speedLimitSound.value) {
            runCatching {
                val tone = android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_MUSIC, 80
                )
                tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 180)
                android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed({ tone.release() }, 450)
            }
        }
        overLimitBeeped = over
    }

    // ── Заставка-часы ──────────────────────────────────────────────
    // Если экран давно не трогали — показываем крупные часы на тёмном
    // фоне и приглушаем подсветку. Любое касание возвращает лаунчер.
    // Это и «ночная лампа» на парковке, и защита экрана от выгорания.
    var lastInteraction by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var saverVisible by remember { mutableStateOf(false) }
    val saverDateFmt = remember {
        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
    }

    // Пока открыт развёрнутый блок (плеер, шторка, инфо) — таймер стоит:
    // заставка не должна вылезать под пальцами.
    // То же, когда в карточку встроено приложение (навигатор, видео):
    // экран «засыпает» под маршрутом — водитель его не трогает,
    // а смотреть на часы вместо дороги нельзя.
    val embeddedAppActive = speedApp != null &&
        SettingsStore.speedCardEmbedded.value && !embedFailed
    val busyUi = shadeOpen || playerExpanded || radioExpanded ||
        carExpanded || carInfoOpen || embeddedAppActive
    DisposableEffect(busyUi) {
        if (!busyUi) lastInteraction = SystemClock.uptimeMillis()
        onDispose {}
    }

    // Вернулись в лаунчер (или экран включился) — таймер с чистого листа.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lastInteraction = SystemClock.uptimeMillis()
                saverVisible = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val saverEnabled = SettingsStore.saverEnabled.value
    val saverTimeoutMs = SettingsStore.saverTimeoutMin.value * 60_000L
    LaunchedEffect(saverEnabled, saverTimeoutMs, saverVisible, busyUi) {
        if (!saverEnabled || saverVisible || busyUi) return@LaunchedEffect
        while (true) {
            if (SystemClock.uptimeMillis() - lastInteraction >= saverTimeoutMs) {
                saverVisible = true
                break
            }
            delay(1000)
        }
    }

    // На время заставки приглушаем подсветку окна — иначе тёмный экран
    // с белыми цифрами ночью всё равно светит как прожектор.
    val saverView = LocalView.current
    DisposableEffect(saverVisible) {
        if (saverVisible) {
            val window = (saverView.context as? android.app.Activity)?.window
            val prev = window?.attributes?.screenBrightness
            window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = 0.05f
                w.attributes = lp
            }
            onDispose {
                window?.let { w ->
                    val lp = w.attributes
                    lp.screenBrightness = prev ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    w.attributes = lp
                }
            }
        } else onDispose {}
    }

    // Границы карточки авто на экране — по ним ляжет плавающее окно
    val cardBounds = remember { android.graphics.Rect() }
    val screenPx = remember {
        val dm = context.resources.displayMetrics
        android.graphics.Point(dm.widthPixels, dm.heightPixels)
    }

    // Тап по спидометру.
    //
    // Поведение как в штатных лаунчерах: тап по спидометру — карточка
    // мгновенно показывает назначенное приложение (карту), повторный
    // тап по кнопке «спидометр» под ним — возвращает спидометр.
    //
    // СПОСОБ ПОКАЗА. На прошивках CC3 виртуальный дисплей (встроенный
    // режим EmbeddedAppView) не рисует чужие приложения — карточка
    // остаётся чёрной. Проверено на Android 8.1: «на весь экран» любое
    // приложение работает, а на виртуальном дисплее — чёрный экран.
    // Поэтому по умолчанию показываем приложение ПЛАВАЮЩИМ ОКНОМ в
    // границах карточки (FreeformLauncher — тот же главный дисплей,
    // приложение рисует). Встроенный режим оставлен как опция
    // «принудительно» для прошивок, где он реально работает, но
    // включается только явным действием, а не по умолчанию.
    val onSpeedClick: () -> Unit = {
        val a = speedApp
        if (a != null) {
            AppRepository.launch(context, a)
        } else {
            modeDialogOpen = true
        }
    }
    // Удержание или нажатие на «кубик» — диалог выбора режима карточки
    // (Спидометр, GPS-карта, Виджет Яндекс Музыки или YouTube)
    val onSpeedLongClick: () -> Unit = {
        modeDialogOpen = true
    }

    // --- общие блоки, которые раскладка расставляет по-разному ---

    val panel = @Composable { mod: Modifier ->
        LauncherPanel(
            modifier = mod,
            time = timeFmt.format(now),
            date = now,
            onAssistant = {
                // Свой офлайн-помощник, если он поднялся. Иначе —
                // системный, чтобы кнопка не оказалась мёртвой.
                if (onVoice != null) onVoice() else runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            onSettings = {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            },
            onSystemSettings = {
                runCatching {
                    context.startActivity(
                        AppRepository.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            onAllApps = { context.startActivity(Intent(context, AllAppsActivity::class.java)) },
            onPickCardApp = {
                modeDialogOpen = true
            },
            onNavigation = { AppRepository.launchFirstAvailable(context, AppRepository.NAVIGATION) }
        )
    }

    val content = @Composable { mod: Modifier ->
        Column(modifier = mod) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.32f),
                horizontalArrangement = Arrangement.spacedBy(dimens().cardGap)
            ) {
                val openPlayer = {
                    if (!MediaControl.hasNotificationAccess(context)) {
                        MediaControl.openNotificationAccessSettings(context)
                    } else {
                        AppRepository.launchFirstAvailable(context, AppRepository.MUSIC)
                    }
                }
                // Вид карточки выбирается по источнику звука, как у штатного
                // лаунчера: телефон по Bluetooth — рамка телефона (обложки
                // всё равно нет), приложение на ГУ — обложка во всю карточку.
                if (spec.phoneMedia && nowPlaying.isBluetooth) {
                    PhoneMediaCard(
                        state = nowPlaying,
                        onPlayPause = { smartPlayPause(context); revision++ },
                        onNext = { MediaControl.next(context); revision++ },
                        onPrev = { MediaControl.previous(context); revision++ },
                        onOpenPlayer = openPlayer,
                        onExpand = { playerExpanded = true },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                } else if (spec.phoneMedia) {
                    CoverMediaCard(
                        state = nowPlaying,
                        onPlayPause = { smartPlayPause(context); revision++ },
                        onNext = { MediaControl.next(context); revision++ },
                        onPrev = { MediaControl.previous(context); revision++ },
                        onOpenPlayer = openPlayer,
                        onExpand = { playerExpanded = true },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                } else {
                    MediaCard(
                        state = nowPlaying,
                        onPlayPause = { smartPlayPause(context); revision++ },
                        onNext = { MediaControl.next(context); revision++ },
                        onPrev = { MediaControl.previous(context); revision++ },
                        onOpenPlayer = openPlayer,
                        onExpand = { playerExpanded = true },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }

                // Карточка авто со статус-баром поверх неё — как у CC3.
                // Box нужен, чтобы значки легли на картинку, а не отняли
                // высоту у карточки.
                Box(modifier = Modifier.weight(1.35f).fillMaxHeight()) {
                CarCard(
                    speedKmh = speedKmh,
                    showSpeed = SettingsStore.showSpeed.value,
                    speedApp = speedApp,
                    onSpeedClick = onSpeedClick,
                    onSpeedLongClick = onSpeedLongClick,
                    onBounds = { r -> cardBounds.set(r) },
                    contentMode = SettingsStore.cardContentMode.value,
                    widgetId = SettingsStore.cardWidgetId.value,
                    onPickWidget = onPickWidget,
                    embeddedPackage = navApp?.packageName,
                    onEmbedFailed = { embedFailed = true },
                    onBackToSpeed = {
                        SettingsStore.setCardContentMode(SettingsStore.CARD_MODE_SPEED)
                        SettingsStore.setSpeedCardEmbedded(false)
                        revision++
                    },
                    onOpenFullscreen = {
                        when (SettingsStore.cardContentMode.value) {
                            SettingsStore.CARD_MODE_MAP, SettingsStore.CARD_MODE_EMBEDDED -> {
                                val navPkg = if (speedApp != null && AppRepository.NAVIGATION.contains(speedApp.packageName)) {
                                    speedApp.packageName
                                } else {
                                    AppRepository.findFirstInstalled(context, AppRepository.NAVIGATION)
                                        ?: speedApp?.packageName
                                        ?: "ru.yandex.yandexmaps"
                                }
                                if (!TaskMover.moveToMainDisplay(context, navPkg)) {
                                    AppRepository.launchPackage(context, navPkg)
                                }
                            }
                            SettingsStore.CARD_MODE_YOUTUBE -> {
                                val ytCandidates = listOf(
                                    "com.google.android.youtube",
                                    "app.revanced.android.youtube",
                                    "com.vanced.android.youtube",
                                    "com.google.android.apps.youtube.mango",
                                    "org.videolan.vlc",
                                    "com.mxtech.videoplayer.ad"
                                )
                                val ytPkg = AppRepository.findFirstInstalled(context, ytCandidates, AppRepository.VIDEO_LABELS)
                                    ?: "com.google.android.youtube"
                                if (!TaskMover.moveToMainDisplay(context, ytPkg)) {
                                    AppRepository.launchFirstAvailable(context, ytCandidates, labels = AppRepository.VIDEO_LABELS)
                                }
                            }
                            else -> {
                                val a = speedApp
                                if (a != null) {
                                    AppRepository.launch(context, a)
                                } else {
                                    AppRepository.launchFirstAvailable(context, AppRepository.NAVIGATION)
                                }
                            }
                        }
                    },
                    onClimate = {
                        AppRepository.launchFirstAvailable(
                            context, AppRepository.CLIMATE,
                            errorText = "Климат-контроль недоступен на этом ГУ"
                        )
                    },
                    onLights = {
                        AppRepository.launchFirstAvailable(
                            context, AppRepository.CAR_INFO,
                            errorText = "Приложение автомобиля не найдено"
                        )
                    },
                    onExpand = { carExpanded = true },
                    modifier = Modifier.fillMaxSize()
                )

                // Строка статуса теперь рисуется одной полосой во всю ширину
                // экрана (в самом низу файла, поверх раскладки), поэтому
                // здесь её больше нет.
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.68f)
                    .padding(top = dimens().cardGap),
                horizontalArrangement = Arrangement.spacedBy(dimens().cardGap)
            ) {
                AppsRowCard(
                    favorites = favorites,
                    onLaunch = { AppRepository.launch(context, it) },
                    onPick = { index ->
                        pickerSlot = favSlots[index]
                        pickerTitle = "Выберите приложение"
                    },
                    modifier = Modifier.weight(1.25f).fillMaxHeight()
                )

                RadioCard(
                    stationName = radioStationName,
                    isPlaying = nowPlaying.isPlaying,
                    onOpen = { AppRepository.launchFirstAvailable(context, AppRepository.RADIO) },
                    onPrev = { MediaControl.previous(context) },
                    onNext = { MediaControl.next(context) },
                    onExpand = { radioExpanded = true },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }


    // Раскладка Aurora: сверху медиа + FM + часы, ниже сетка приложений, внизу док.
    val auroraContent = @Composable { mod: Modifier ->
        Column(modifier = mod) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaCard(
                    state = nowPlaying,
                    onPlayPause = { smartPlayPause(context); revision++ },
                    onNext = { MediaControl.next(context); revision++ },
                    onPrev = { MediaControl.previous(context); revision++ },
                    onOpenPlayer = {
                        if (!MediaControl.hasNotificationAccess(context)) {
                            MediaControl.openNotificationAccessSettings(context)
                        } else {
                            AppRepository.launchFirstAvailable(context, AppRepository.MUSIC)
                        }
                    },
                    onExpand = { playerExpanded = true },
                    modifier = Modifier.weight(1.05f).fillMaxHeight()
                )

                FmRadioCard(
                    stationName = radioStationName,
                    onPrev = { AppRepository.launchFirstAvailable(context, AppRepository.RADIO) },
                    onNext = { AppRepository.launchFirstAvailable(context, AppRepository.RADIO) },
                    onOpen = { AppRepository.launchFirstAvailable(context, AppRepository.RADIO) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                Column(
                    modifier = Modifier.weight(0.95f).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    // Часы и погода под ними — единый верхний блок,
                    // прижатый к верху колонки.
                    Column(horizontalAlignment = Alignment.End) {
                        HeroClockPanel(date = now)
                        // Строка погоды: та же погода, что в статус-баре
                        // (общий ключ обновления), но под часами она видна
                        // постоянно, не только в верхней полосе.
                        val weather by rememberWeather(weatherKey)
                        if (weather.valid) {
                            AuroraWeatherLine(
                                weather = weather,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    val heading = TripComputer.headingDeg.value
                    CompassCard(
                        direction = compassRose(heading),
                        // Направление движения по GPS; «—», если машина ещё
                        // ни разу не ехала с пойманным сигналом.
                        onClick = {
                            AppRepository.launchFirstAvailable(context, AppRepository.NAVIGATION)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                }
            }

            // Сетка приложений прямо на фоне
            AppGridRow(
                apps = favorites + listOf(null),
                onLaunch = { AppRepository.launch(context, it) },
                onPick = { index ->
                    if (index < favSlots.size) {
                        pickerSlot = favSlots[index]
                        pickerTitle = "Выберите приложение"
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(spec.bgBrush)
            .launcherGestures(
                onNextTrack = {
                    MediaControl.next(context)
                    feedback.show(GestureAction.NextTrack)
                    revision++
                },
                onPrevTrack = {
                    MediaControl.previous(context)
                    feedback.show(GestureAction.PrevTrack)
                    revision++
                },
                onVolumeStep = { up ->
                    val level = MediaControl.stepVolume(context, up)
                    feedback.show(GestureAction.Volume, level)
                },
                enabled = SettingsStore.gesturesEnabled.value
            )
            // Любое касание по лаунчеру сбрасывает таймер бездействия:
            // заставка появляется, только когда экран действительно
            // бросили. События ловим раньше всех (Initial pass), чтобы
            // сброс работал даже для жестов, съедаемых детьми.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    lastInteraction = SystemClock.uptimeMillis()
                }
            }
    ) {
        // Размеры считаем от реального экрана: у ГУ 1280x720 бывает
        // и 160, и 320 dpi — фиксированные dp ломали бы раскладку.
        val d = remember(maxWidth, maxHeight) { calcDimens(maxWidth, maxHeight) }

        CompositionLocalProvider(LocalDimens provides d) {
        Box(modifier = Modifier.fillMaxSize()) {

        // Свои обои под всем интерфейсом
        WallpaperStore.bitmap.value?.let { wp ->
            androidx.compose.foundation.Image(
                bitmap = wp.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(spec.bg.first().copy(alpha = WallpaperStore.dim.value))
            )
        }

        // Свечение по углам — поверх фона и обоев, но под карточками.
        // Плоский градиент выглядел бумажно; от подсветки экран кажется
        // глубже, а карточки приподнятыми. С обоями не рисуем: там своя
        // картинка, и затемнение по краям её только пачкает.
        if (WallpaperStore.bitmap.value == null) {
            Vignette(accent = spec.accent, accent2 = spec.accent2)
        }


        // Статус-бар отдельной полосой больше не рисуем: у штатного
        // лаунчера он наложен поверх правой карточки, а панель и карточки
        // идут от самого края экрана. Раньше три отступа подряд
        // (statusBarsPadding + высота полосы + паддинг карточек) съедали
        // сотню пикселей сверху — экран выглядел полупустым.
        Column(modifier = Modifier.fillMaxSize()) {

        // Полоса статуса объявлена внутри правой части раскладки
        // (см. contentWithStatus ниже), а не здесь на всю ширину.
        // У CC3 она начинается после боковой панели: над часами
        // и орбом ничего не висит.

        val outer = Modifier
            .fillMaxSize()
            .padding(
                start = d.screenPadding, end = d.screenPadding,
                top = 0.dp, bottom = d.screenPadding * 0.5f
            )

        // Правая часть экрана: сверху полоса статуса, под ней карточки.
        // Панель остаётся слева на всю высоту и полосой не перекрывается —
        // именно так на CC3.
        val contentWithStatus = @Composable { mod: Modifier ->
            Column(modifier = mod) {
                TopStatusStrip(
                    weatherKey = weatherKey,
                    onOpenShade = { shadeOpen = true },
                    // Тот же помощник, что на орбе панели: микрофон
                    // в строке — просто вторая точка входа.
                    onVoice = onVoice,
                    modifier = Modifier.fillMaxWidth()
                )
                content(Modifier.fillMaxWidth().weight(1f))
            }
        }

        // Раскладка целиком зависит от темы: панель может быть слева,
        // справа, снизу доком или сверху строкой.
        when (spec.layout) {
            LayoutStyle.SidebarLeft -> Row(outer, horizontalArrangement = Arrangement.spacedBy(d.cardGap)) {
                panel(Modifier.width(d.panelWidth).fillMaxHeight())
                contentWithStatus(Modifier.weight(1f).fillMaxHeight())
            }
            LayoutStyle.SidebarRight -> Row(outer, horizontalArrangement = Arrangement.spacedBy(d.cardGap)) {
                contentWithStatus(Modifier.weight(1f).fillMaxHeight())
                panel(Modifier.width(d.panelWidth).fillMaxHeight())
            }
            LayoutStyle.BottomDock -> Column(outer, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content(Modifier.fillMaxWidth().weight(1f))
                panel(Modifier.fillMaxWidth().height(d.dockHeight + 22.dp))
            }
            LayoutStyle.TopBar -> Column(outer, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                panel(Modifier.fillMaxWidth().height(d.dockHeight + 16.dp))
                content(Modifier.fillMaxWidth().weight(1f))
            }
            LayoutStyle.GridDock -> Column(outer, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                auroraContent(Modifier.fillMaxWidth().weight(1f))
                BottomDock(
                    items = listOf(
                        Icons.AutoMirrored.Rounded.Send to {
                            AppRepository.launchFirstAvailable(context, AppRepository.NAVIGATION)
                        },
                        Icons.Rounded.Phone to {
                            AppRepository.launchFirstAvailable(
                                context, AppRepository.PHONE,
                                fallback = AppRepository.dialerFallback()
                            )
                        },
                        Icons.Rounded.Radio to {
                            AppRepository.launchFirstAvailable(context, AppRepository.RADIO)
                        },
                        Icons.Rounded.Circle to {
                            context.startActivity(Intent(context, AllAppsActivity::class.java))
                        },
                        Icons.Rounded.MusicNote to {
                            AppRepository.launchFirstAvailable(context, AppRepository.MUSIC)
                        },
                        Icons.Rounded.PlayCircle to {
                            AppRepository.launchFirstAvailable(
                                context, AppRepository.VIDEO,
                                fallback = AppRepository.galleryFallback()
                            )
                        },
                        Icons.Rounded.Settings to {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }
                    ),
                    highlightIndex = 3,
                    modifier = Modifier.fillMaxWidth().height(d.dockHeight)
                )
            }
            LayoutStyle.TriPanel -> Column(outer) {
                TopStatusStrip(
                    weatherKey = weatherKey,
                    onOpenShade = { shadeOpen = true },
                    onVoice = onVoice,
                    modifier = Modifier.fillMaxWidth()
                )
                TeyesTriPanel(
                    apps = apps,
                    favorites = favorites,
                    nowPlaying = nowPlaying,
                    speedKmh = speedKmh,
                    speedApp = speedApp,
                    onSpeedClick = onSpeedClick,
                    onSpeedLongClick = onSpeedLongClick,
                    onPickSlot = { index ->
                        if (index < favSlots.size) {
                            pickerSlot = favSlots[index]
                            pickerTitle = "Выберите приложение"
                        }
                    },
                    onPickWidget = onPickWidget,
                    onPlayPause = { smartPlayPause(context); revision++ },
                    onNext = { MediaControl.next(context); revision++ },
                    onPrev = { MediaControl.previous(context); revision++ },
                    onOpenPlayer = {
                        if (!MediaControl.hasNotificationAccess(context)) {
                            MediaControl.openNotificationAccessSettings(context)
                        } else {
                            AppRepository.launchFirstAvailable(context, AppRepository.MUSIC)
                        }
                    },
                    onBounds = { r -> cardBounds.set(r) },
                    onEmbedFailed = { embedFailed = true },
                    onBackToSpeed = {
                        FreeformLauncher.closeActiveWindow(context)
                        SettingsStore.setCardContentMode(SettingsStore.CARD_MODE_SPEED)
                        revision++
                    },
                    navApp = navApp,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
        }

        // Узкая полоса захвата у верхнего края: тянуть вниз — шторка.
        // Объявлена после строки статуса, но значки в ней остаются
        // нажимаемыми, потому что здесь ловится только перетаскивание.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .shadePullDown(d.statusBarHeight) { shadeOpen = true }
        )

        // Шторка объявлена после строки статуса, значит рисуется поверх
        // неё и перехватывает нажатия — иначе кнопки под затемнением
        // оставались бы кликабельными.
        ControlShade(
            visible = shadeOpen,
            onDismiss = { shadeOpen = false },
            onScreenOff = { onScreenOff?.invoke() },
            weatherKey = weatherKey
        )

        SetupBanner(
            visible = !isDefaultLauncher && !bannerDismissed,
            onFix = { DefaultLauncherCheck.openChooser(context) },
            onDismiss = { bannerDismissed = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )

        ExpandedPlayer(
            visible = playerExpanded,
            state = nowPlaying,
            onPlayPause = { smartPlayPause(context); revision++ },
            onNext = { MediaControl.next(context); revision++ },
            onPrev = { MediaControl.previous(context); revision++ },
            onOpenApp = {
                playerExpanded = false
                AppRepository.launchFirstAvailable(context, AppRepository.MUSIC)
            },
            onSeek = { pos -> MediaControl.seekTo(context, pos); revision++ },
            canSeek = MediaControl.canSeek(),
            onClose = { playerExpanded = false }
        )

        ExpandedRadio(
            visible = radioExpanded,
            stationName = radioStationName,
            frequency = null,
            isPlaying = nowPlaying.isPlaying,
            onPrev = { MediaControl.previous(context) },
            onNext = { MediaControl.next(context) },
            onOpen = {
                radioExpanded = false
                AppRepository.launchFirstAvailable(context, AppRepository.RADIO)
            },
            onClose = { radioExpanded = false }
        )

        ExpandedCar(
            visible = carExpanded,
            speedKmh = speedKmh,
            onResetTrip = { TripComputer.reset(); revision++ },
            onOpenCarInfo = { carExpanded = false; carInfoOpen = true },
            onClose = { carExpanded = false }
        )

        // Пробег, ТО и журнал поездок. Отдельным экраном, а не внутри
        // развёрнутой карточки: там уже тесно, а списку поездок нужна
        // вся высота.
        if (carInfoOpen) {
            CarInfoScreen(onClose = { carInfoOpen = false })
        }

        // Ночное затемнение — поверх интерфейса, но под подсказками жестов
        NightDim(isNight)

        GestureOverlay(feedback)

        // Заставка-часы — поверх всего, включая жесты: пока она видна,
        // лаунчер должен спать. Любое касание — и лаунчер проснулся.
        // Появляется плавно, а не «вспыхивает» — на тёмном фоне резкое
        // появление цифр бьёт по глазам ночью.
        AnimatedVisibility(visible = saverVisible, enter = fadeIn(tween(450))) {
            ScreenSaverClock(
                time = timeFmt.format(now),
                date = saverDateFmt.format(now),
                onWake = {
                    saverVisible = false
                    lastInteraction = SystemClock.uptimeMillis()
                }
            )
        }
        }
        }
    }

    if (modeDialogOpen) {
        CardContentModeDialog(
            currentMode = SettingsStore.cardContentMode.value,
            onSelectMode = { mode ->
                SettingsStore.setCardContentMode(mode)
                revision++
                if (mode == SettingsStore.CARD_MODE_MAP || mode == SettingsStore.CARD_MODE_YOUTUBE) {
                    val targetPkg = if (mode == SettingsStore.CARD_MODE_MAP) {
                        val navCandidates = listOf(
                            "ru.yandex.yandexnavi",
                            "ru.yandex.yandexmaps",
                            "ru.dublgis.dgismobile",
                            "com.google.android.apps.maps",
                            "com.waze",
                            "cityguide.probki.net",
                            "com.navitel",
                            "com.sygic.aura"
                        )
                        AppRepository.findFirstInstalled(context, navCandidates, listOf("Навигатор", "Карты", "Яндекс Навигатор", "2ГИС", "Maps"))
                            ?: "ru.yandex.yandexnavi"
                    } else {
                        val ytCandidates = listOf(
                            "com.google.android.youtube",
                            "app.revanced.android.youtube",
                            "com.vanced.android.youtube",
                            "com.google.android.apps.youtube.mango",
                            "org.videolan.vlc",
                            "com.mxtech.videoplayer.ad"
                        )
                        AppRepository.findFirstInstalled(context, ytCandidates, AppRepository.VIDEO_LABELS)
                            ?: "com.google.android.youtube"
                    FreeformLauncher.closeActiveWindow(context)
                } else if (mode == SettingsStore.CARD_MODE_SPEED) {
                    FreeformLauncher.closeActiveWindow(context)
                }
            },
            onPickAppForSpeed = {
                pickerSlot = ShortcutStore.SLOT_SPEED
                pickerTitle = "Что открывать при нажатии на спидометр"
            },
            onPickWidget = onPickWidget,
            onDismiss = { modeDialogOpen = false }
        )
    }

    pickerSlot?.let { slot ->
        AppPickerDialog(
            apps = apps,
            title = pickerTitle,
            onPick = { app ->
                store.set(slot, app.packageName)
                revision++
                pickerSlot = null
                // Сразу показываем результат: карту открываем плавающим
                // окном по границам карточки (на главном дисплее, где
                // приложение гарантированно рисует). Встроенный режим
                // не включаем — на прошивках CC3 он даёт чёрный экран.
                // Если ГУ не умеет окна — launchFreeform сам откроет
                // приложение на весь экран и предупредит.
                if (slot == ShortcutStore.SLOT_SPEED) {
                    embedFailed = false
                    launchFreeform(app.packageName)
                }
            },
            onReset = { store.clear(slot); revision++; pickerSlot = null },
            onDismiss = { pickerSlot = null }
        )
    }
}

/**
 * Умный play/pause.
 *
 * На китайских ГУ штатное BT-приложение держит аудиоканал: пока его
 * не поднять, телефон играет «в никуда» и обычный play уходит впустую.
 * Раньше это лечилось только при подключении телефона, но магнитола
 * часто просыпается уже с подключённым устройством и брошкаст мы
 * пропускаем.
 *
 * Поэтому: если сессии нет вообще — сначала поднимаем канал, и только
 * потом играем. Если сессия есть, ведём себя как обычно.
 */
private fun smartPlayPause(context: android.content.Context) {
    val now = MediaControl.read(context)
    if (!now.hasSession) {
        BtMusicStarter.ensureChannel(context)
        return
    }
    MediaControl.playPause(context)
}

/**
 * Заставка-часы: чёрный экран с крупным временем.
 *
 * Появляется после долгого бездействия (см. HomeScreen), подсветка в это
 * время приглушена отдельным эффектом. Любое касание будит лаунчер —
 * поэтому здесь нет ни кнопок, ни подсказок: тап в любом месте.
 */
/**
 * Румб по курсу движения (0° — север, 90° — восток).
 * heading < 0 — курс ещё не известен (не было движения с GPS).
 */
private fun compassRose(heading: Int): String {
    if (heading < 0) return "—\nкурс"
    val names = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
    val idx = ((heading + 22) / 45).toInt() % 8
    return "${names[idx]}\n$heading°"
}

@Composable
private fun ScreenSaverClock(time: String, date: String, onWake: () -> Unit) {
    val s = LocalThemeSpec.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05060A))
            // Любое событие (не только «чистый» тап) — проснуться.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onWake()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                color = Color(0xFFE8EAED),
                fontSize = 128.sp,
                fontWeight = FontWeight.ExtraLight,
                fontFamily = s.fontFamily,
                letterSpacing = 4.sp
            )
            Text(
                text = date,
                color = Color(0x99E8EAED),
                fontSize = 20.sp,
                fontFamily = s.fontFamily,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
