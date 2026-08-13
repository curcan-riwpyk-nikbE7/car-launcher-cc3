package com.example.carlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.carlauncher.AllAppsActivity
import com.example.carlauncher.SettingsActivity
import com.example.carlauncher.data.AppInfo
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.carlauncher.data.AppRepository
import com.example.carlauncher.data.DefaultLauncherCheck
import com.example.carlauncher.data.PackageChangeEffect
import com.example.carlauncher.data.MediaControl
import com.example.carlauncher.data.SettingsStore
import com.example.carlauncher.data.FreeformLauncher
import com.example.carlauncher.data.SplitScreen
import androidx.compose.ui.graphics.asImageBitmap
import com.example.carlauncher.data.TripComputer
import com.example.carlauncher.data.WallpaperStore
import com.example.carlauncher.data.rememberIsNight
import com.example.carlauncher.data.ShortcutStore
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
    onVoice: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val spec = LocalThemeSpec.current
    val store = remember { ShortcutStore(context) }
    var revision by remember { mutableStateOf(0) }
    var pickerSlot by remember { mutableStateOf<String?>(null) }
    var pickerTitle by remember { mutableStateOf("") }

    val nowPlaying by rememberNowPlaying(revision)
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
            store.get(slot)?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
        }
    }

    // Приложение, назначенное на виджет спидометра
    val speedApp = remember(revision, apps) {
        store.get(ShortcutStore.SLOT_SPEED)?.let { pkg ->
            apps.firstOrNull { it.packageName == pkg }
        }
    }

    var playerExpanded by remember { mutableStateOf(false) }
    var radioExpanded by remember { mutableStateOf(false) }
    var carExpanded by remember { mutableStateOf(false) }
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
    var showSpeedChoice by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf("embed") }
    // Приложение, встроенное в карточку прямо сейчас
    var embedFailed by remember { mutableStateOf(false) }
    // Границы карточки авто на экране — по ним ляжет плавающее окно
    val cardBounds = remember { android.graphics.Rect() }
    val screenPx = remember {
        val dm = context.resources.displayMetrics
        android.graphics.Point(dm.widthPixels, dm.heightPixels)
    }

    // Запуск в плавающем окне выбранной области
    val launchFreeform: (String) -> Unit = { pkg ->
        val area = runCatching {
            FreeformLauncher.Area.valueOf(SettingsStore.speedArea.value)
        }.getOrDefault(FreeformLauncher.Area.RightColumn)
        val b = FreeformLauncher.boundsFor(area, cardBounds, screenPx.x, screenPx.y)
        if (b.isEmpty) AppRepository.launchPackage(context, pkg)
        else FreeformLauncher.launchInBounds(
            context, pkg, b,
            // Видео и карты сначала открываем на весь экран, иначе они
            // не желают рисоваться в окне. Для остальных приложений
            // лишний шаг не нужен — он заметен глазом.
            prewarm = SettingsStore.prewarmWindow.value && FreeformLauncher.needsPrewarm(pkg)
        )
    }

    // Тап по спидометру: запустить назначенное приложение в выбранном режиме
    val onSpeedClick: () -> Unit = {
        val a = speedApp
        if (a != null) {
            when (SettingsStore.speedMode.value) {
                // В режиме встраивания приложение уже живёт в карточке
                "embed" -> if (embedFailed) AppRepository.launch(context, a) else Unit
                // Плавающее окно ровно в границах карточки — ближе всего
                // к тому, как это выглядит в фирменных прошивках
                "freeform" -> launchFreeform(a.packageName)
                "split" -> SplitScreen.launchBeside(context, a.packageName)
                else -> AppRepository.launch(context, a)
            }
        } else {
            showSpeedChoice = true
        }
    }
    val onSpeedLongClick: () -> Unit = { showSpeedChoice = true }

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
                        onPlayPause = { MediaControl.playPause(context); revision++ },
                        onNext = { MediaControl.next(context); revision++ },
                        onPrev = { MediaControl.previous(context); revision++ },
                        onOpenPlayer = openPlayer,
                        onExpand = { playerExpanded = true },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                } else if (spec.phoneMedia) {
                    CoverMediaCard(
                        state = nowPlaying,
                        onPlayPause = { MediaControl.playPause(context); revision++ },
                        onNext = { MediaControl.next(context); revision++ },
                        onPrev = { MediaControl.previous(context); revision++ },
                        onOpenPlayer = openPlayer,
                        onExpand = { playerExpanded = true },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                } else {
                    MediaCard(
                        state = nowPlaying,
                        onPlayPause = { MediaControl.playPause(context); revision++ },
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
                    embeddedPackage = speedApp?.packageName
                        ?.takeIf { SettingsStore.speedMode.value == "embed" && !embedFailed },
                    onEmbedFailed = { embedFailed = true },
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

                // Дата, погода и значки — в правом верхнем углу карточки.
                // Своей высоты не занимают, поэтому карточки стали выше
                // почти на сотню пикселей.
                TopStatusBar(
                    weatherKey = weatherKey,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .height(dimens().statusBarHeight)
                        .padding(horizontal = 14.dp)
                )
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
                    stationName = SettingsStore.radioName.value,
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
                    onPlayPause = { MediaControl.playPause(context); revision++ },
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
                    frequency = "87.50",
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
                    HeroClockPanel(date = now)
                    CompassCard(
                        direction = "North\nWest",
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


        // Статус-бар отдельной полосой больше не рисуем: у штатного
        // лаунчера он наложен поверх правой карточки, а панель и карточки
        // идут от самого края экрана. Раньше три отступа подряд
        // (statusBarsPadding + высота полосы + паддинг карточек) съедали
        // сотню пикселей сверху — экран выглядел полупустым.
        Column(modifier = Modifier.fillMaxSize()) {

        val outer = Modifier
            .fillMaxSize()
            .padding(
                start = d.screenPadding, end = d.screenPadding,
                top = d.screenPadding * 0.5f, bottom = d.screenPadding * 0.5f
            )

        // Раскладка целиком зависит от темы: панель может быть слева,
        // справа, снизу доком или сверху строкой.
        when (spec.layout) {
            LayoutStyle.SidebarLeft -> Row(outer, horizontalArrangement = Arrangement.spacedBy(d.cardGap)) {
                panel(Modifier.width(d.panelWidth).fillMaxHeight())
                content(Modifier.weight(1f).fillMaxHeight())
            }
            LayoutStyle.SidebarRight -> Row(outer, horizontalArrangement = Arrangement.spacedBy(d.cardGap)) {
                content(Modifier.weight(1f).fillMaxHeight())
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
        }
        }

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
            onPlayPause = { MediaControl.playPause(context); revision++ },
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
            stationName = SettingsStore.radioName.value,
            frequency = "87.50",
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
            onClose = { carExpanded = false }
        )

        // Ночное затемнение — поверх интерфейса, но под подсказками жестов
        NightDim(isNight)

        GestureOverlay(feedback)
        }
        }
    }



    if (showSpeedChoice) {
        LaunchModeDialog(
            currentApp = speedApp?.label,
            freeformAvailable = FreeformLauncher.isAvailable(context),
            currentArea = SettingsStore.speedArea.value,
            onAreaChange = { SettingsStore.setSpeedArea(it) },
            onPickEmbed = {
                showSpeedChoice = false
                pendingMode = "embed"
                embedFailed = false
                SettingsStore.setSpeedMode("embed")
                pickerSlot = ShortcutStore.SLOT_SPEED
                pickerTitle = "Какое приложение встроить в карточку"
            },
            onPickFreeform = {
                showSpeedChoice = false
                pendingMode = "freeform"
                SettingsStore.setSpeedMode("freeform")
                pickerSlot = ShortcutStore.SLOT_SPEED
                pickerTitle = "Какое приложение показывать в карточке"
            },
            onPickSplit = {
                showSpeedChoice = false
                pendingMode = "split"
                SettingsStore.setSpeedMode("split")
                pickerSlot = ShortcutStore.SLOT_SPEED
                pickerTitle = "Какое приложение открывать рядом"
            },
            onPickFullscreen = {
                showSpeedChoice = false
                pendingMode = "full"
                SettingsStore.setSpeedMode("full")
                pickerSlot = ShortcutStore.SLOT_SPEED
                pickerTitle = "Какое приложение запускать"
            },
            onClearApp = {
                showSpeedChoice = false
                store.clear(ShortcutStore.SLOT_SPEED)
                revision++
            },
            onDismiss = { showSpeedChoice = false }
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
                // Сразу показываем результат: запускаем в выбранном режиме
                if (slot == ShortcutStore.SLOT_SPEED) {
                    when (pendingMode) {
                        // Встроенное приложение стартует само при отрисовке
                        "embed" -> embedFailed = false
                        "freeform" -> launchFreeform(app.packageName)
                        "split" -> SplitScreen.launchBeside(context, app.packageName)
                        else -> AppRepository.launch(context, app)
                    }
                }
            },
            onReset = { store.clear(slot); revision++; pickerSlot = null },
            onDismiss = { pickerSlot = null }
        )
    }
}
