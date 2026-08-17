package com.example.carlauncher.ui

import com.example.carlauncher.data.BtMusicStarter
import com.example.carlauncher.data.SettingsStore
import com.example.carlauncher.data.UpdateChecker
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.ui.platform.LocalContext
import com.example.carlauncher.data.MotionSensors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.AppInfo
import kotlin.math.roundToInt

/** Модель одной строки-ярлыка в настройках. */
data class ShortcutRow(
    val slot: String,
    val title: String,
    val app: AppInfo?
)

/** Разделы настроек — вкладки-пилюли сверху, как на магнитоле. */
private enum class SettingsTab(val title: String, val icon: ImageVector) {
    Appearance("Оформление", Icons.Rounded.Palette),
    Voice("Голос", Icons.Rounded.Mic),
    Gestures("Жесты", Icons.Rounded.Gesture),
    Shortcuts("Приложения", Icons.Rounded.Apps),
    Screen("Экран", Icons.Rounded.Brightness6),
    System("Система", Icons.Rounded.Build)
}

/**
 * Настройки в стиле штатного меню магнитолы:
 * вкладки-пилюли сверху, крупные плитки снизу.
 * Плитки большие намеренно — в них удобно попадать на ходу.
 */
@Composable
fun SettingsScreen(
    themeId: String,
    onThemePick: (String) -> Unit,
    gesturesEnabled: Boolean,
    onGestures: (Boolean) -> Unit,
    swipeThreshold: Float,
    onSwipe: (Float) -> Unit,
    volumeStep: Float,
    onVolumeStep: (Float) -> Unit,
    haptic: Boolean,
    onHaptic: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOn: (Boolean) -> Unit,
    immersive: Boolean,
    onImmersive: (Boolean) -> Unit,
    showSpeed: Boolean,
    onShowSpeed: (Boolean) -> Unit,
    nightMode: Boolean,
    onNightMode: (Boolean) -> Unit,
    btAutoPlay: Boolean,
    onBtAutoPlay: (Boolean) -> Unit,
    onPickBtApp: () -> Unit = {},
    onOpenCarSettings: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    btRevision: Int = 0,
    speedMode: String,
    onSpeedMode: (String) -> Unit,
    hasWallpaper: Boolean,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    shortcuts: List<ShortcutRow>,
    onShortcutPick: (String) -> Unit,
    onShortcutClear: (String) -> Unit,
    hasNotificationAccess: Boolean,
    onNotificationAccess: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    val s = LocalThemeSpec.current
    var tab by remember { mutableStateOf(SettingsTab.Appearance) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(s.bgBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // ── Ряд вкладок ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(s.cardBg)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack, "Назад",
                        tint = s.textPrimary, modifier = Modifier.size(20.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsTab.entries.forEach { t ->
                        TabPill(
                            title = t.title,
                            icon = t.icon,
                            selected = t == tab,
                            onClick = { tab = t }
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(top = 14.dp)) {
                when (tab) {
                    SettingsTab.Appearance -> AppearanceTab(themeId, onThemePick)
                    SettingsTab.Voice -> VoiceTab()
                    SettingsTab.Gestures -> GesturesTab(
                        gesturesEnabled, onGestures, swipeThreshold, onSwipe,
                        volumeStep, onVolumeStep, haptic, onHaptic
                    )
                    SettingsTab.Shortcuts -> ShortcutsTab(shortcuts, onShortcutPick, onShortcutClear)
                    SettingsTab.Screen -> ScreenTab(
                        keepScreenOn, onKeepScreenOn, immersive, onImmersive,
                        showSpeed, onShowSpeed, nightMode, onNightMode,
                        hasWallpaper, onPickWallpaper, onClearWallpaper
                    )
                    SettingsTab.System -> SystemTab(
                        hasNotificationAccess, onNotificationAccess,
                        btAutoPlay, onBtAutoPlay, onPickBtApp, onOpenCarSettings,
                        onOpenDiagnostics, btRevision,
                        speedMode, onSpeedMode, onReset
                    )
                }
            }
        }
    }
}

/** Вкладка-пилюля. Активная — с градиентной рамкой, как в оригинале. */
@Composable
private fun TabPill(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val s = LocalThemeSpec.current
    val shape = RoundedCornerShape(26.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .clip(shape)
            .background(if (selected) s.cardBg else s.cardBg.copy(alpha = 0.55f))
            .then(
                if (selected) Modifier.border(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(listOf(s.accent, s.accent2)),
                    shape = shape
                ) else Modifier.border(1.dp, s.cardStroke, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
    ) {
        Icon(
            icon, null,
            tint = if (selected) s.textPrimary else s.textSecondary,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = title,
            color = if (selected) s.textPrimary else s.textSecondary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            fontFamily = s.fontFamily,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

/**
 * Крупная плитка. Иконка по центру, подпись под ней —
 * повторяет геометрию штатного меню магнитолы.
 */
@Composable
internal fun SettingTile(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    accentIcon: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(s.cardBg)
            .border(s.strokeWidth, s.cardStroke.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (accentIcon) s.accent else s.textPrimary.copy(alpha = 0.9f),
            modifier = Modifier.size(38.dp)
        )
        Text(
            text = title,
            color = s.textPrimary,
            fontSize = 14.sp,
            fontFamily = s.fontFamily,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = s.textSecondary,
                fontSize = 11.sp,
                fontFamily = s.fontFamily,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(top = 10.dp)) { trailing() }
        }
    }
}

// ─────────────────────────── Вкладка «Оформление» ───────────────────────────

@Composable
private fun AppearanceTab(themeId: String, onPick: (String) -> Unit) {
    val s = LocalThemeSpec.current
    val current = remember(themeId) { themeById(themeId) }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Слева — как будет выглядеть выбранная тема. Раньше здесь были
        // пять полос с градиентом на весь экран: они занимали всё место
        // и при этом не показывали ни раскладку, ни фон, ни карточки.
        Column(modifier = Modifier.weight(1.15f)) {
            Text(
                "ТАК БУДЕТ ВЫГЛЯДЕТЬ",
                color = s.textDim,
                fontSize = 12.sp,
                fontFamily = s.fontFamily,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            ThemePreview(
                spec = current,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        // Справа — сам выбор. Строки компактные: пять штук занимают
        // вдвое меньше места, чем прежние полосы, а говорят больше.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "ТЕМА",
                color = s.textDim,
                fontSize = 12.sp,
                fontFamily = s.fontFamily,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AllThemes, key = { it.id }) { t ->
                    ThemeRow(spec = t, selected = t.id == themeId, onClick = { onPick(t.id) })
                }
            }
        }
    }
}

/**
 * Строка выбора темы: образец цвета, название и чем она отличается.
 *
 * Подпись важнее, чем кажется: Violet и Blue по одному градиенту
 * почти неразличимы, а «фиолетовый как на CC3» и «сине-бирюзовый
 * холодный» — уже понятно.
 */
@Composable
private fun ThemeRow(spec: ThemeSpec, selected: Boolean, onClick: () -> Unit) {
    val active = LocalThemeSpec.current
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) active.cardBg else Color.White.copy(alpha = 0.06f))
            .then(
                if (selected) Modifier.border(2.dp, active.accent, shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(listOf(spec.accent, spec.accent2, spec.bg.last()))
                )
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                spec.title,
                color = if (selected) active.accent else active.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = active.fontFamily
            )
            Text(
                spec.subtitle,
                color = active.textDim,
                fontSize = 12.sp,
                fontFamily = active.fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(active.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Check, null,
                    tint = active.onAccent,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}


/** Схематичная раскладка темы. */
@Composable
private fun ThemeMini(s: ThemeSpec) {
    val panel = @Composable { m: Modifier ->
        Box(m.clip(RoundedCornerShape(3.dp)).background(s.panelBg))
    }
    val cards = @Composable { m: Modifier ->
        Column(m, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.verticalGradient(s.mediaGradient))
                )
                Box(
                    Modifier.weight(1.2f).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(s.carCardBg)
                )
            }
            Box(
                Modifier.fillMaxWidth().weight(0.8f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(s.cardBg)
            )
        }
    }
    when (s.layout) {
        LayoutStyle.SidebarLeft -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            panel(Modifier.width(11.dp).fillMaxHeight()); cards(Modifier.weight(1f).fillMaxHeight())
        }
        LayoutStyle.SidebarRight -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cards(Modifier.weight(1f).fillMaxHeight()); panel(Modifier.width(11.dp).fillMaxHeight())
        }
        LayoutStyle.BottomDock, LayoutStyle.GridDock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cards(Modifier.fillMaxWidth().weight(1f)); panel(Modifier.fillMaxWidth().height(11.dp))
        }
        LayoutStyle.TopBar -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            panel(Modifier.fillMaxWidth().height(11.dp)); cards(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

// ───────────────────────────── Вкладка «Жесты» ──────────────────────────────

@Composable
private fun GesturesTab(
    enabled: Boolean, onEnabled: (Boolean) -> Unit,
    swipe: Float, onSwipe: (Float) -> Unit,
    volume: Float, onVolume: (Float) -> Unit,
    haptic: Boolean, onHaptic: (Boolean) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SettingTile(
                icon = Icons.Rounded.Gesture,
                title = "Управление жестами",
                subtitle = if (enabled) "Включено" else "Выключено",
                accentIcon = enabled,
                trailing = { ThemedSwitch(enabled, onEnabled) },
                onClick = { onEnabled(!enabled) },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            SliderTile(
                icon = Icons.Rounded.SwipeVertical,
                title = "Порог смены трека",
                value = swipe, range = 40f..160f, onChange = onSwipe,
                hint = "Больше — сложнее задеть"
            )
        }
        item {
            SliderTile(
                icon = Icons.Rounded.Tune,
                title = "Шаг громкости",
                value = volume, range = 20f..90f, onChange = onVolume,
                hint = "Тянуть на одно деление"
            )
        }
        item {
            SettingTile(
                icon = Icons.Rounded.Vibration,
                title = "Вибро-отклик",
                subtitle = if (haptic) "Включено" else "Выключено",
                accentIcon = haptic,
                trailing = { ThemedSwitch(haptic, onHaptic) },
                onClick = { onHaptic(!haptic) },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
    }
}

@Composable
private fun SliderTile(
    icon: ImageVector,
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    hint: String,
    onChange: (Float) -> Unit
) {
    val s = LocalThemeSpec.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(s.cardBg)
            .border(s.strokeWidth, s.cardStroke.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, title, tint = s.textPrimary.copy(alpha = 0.9f), modifier = Modifier.size(34.dp))
        Text(
            text = title, color = s.textPrimary, fontSize = 13.sp,
            fontFamily = s.fontFamily, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = "${value.roundToInt()} dp", color = s.accent, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold, fontFamily = s.fontFamily,
            modifier = Modifier.padding(top = 4.dp)
        )
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = s.accent,
                activeTrackColor = s.accent,
                inactiveTrackColor = s.textPrimary.copy(alpha = 0.14f)
            ),
            modifier = Modifier.height(28.dp)
        )
        Text(
            text = hint, color = s.textDim, fontSize = 10.sp,
            fontFamily = s.fontFamily, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ────────────────────────── Вкладка «Приложения» ────────────────────────────

@Composable
private fun ShortcutsTab(
    shortcuts: List<ShortcutRow>,
    onPick: (String) -> Unit,
    onClear: (String) -> Unit
) {
    val s = LocalThemeSpec.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItems(shortcuts, key = { it.slot }) { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(s.cardBg)
                    .border(s.strokeWidth, s.cardStroke.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable { onPick(row.slot) }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(s.iconCorner))
                        .background(s.textPrimary.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (row.app != null) {
                        AppIcon(row.app.icon, row.app.label, Modifier.size(36.dp))
                    } else {
                        Icon(Icons.Rounded.Apps, null, tint = s.textDim, modifier = Modifier.size(26.dp))
                    }
                }
                Text(
                    text = row.title, color = s.textSecondary, fontSize = 11.sp,
                    fontFamily = s.fontFamily, modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = row.app?.label ?: "Не назначено",
                    color = if (row.app != null) s.textPrimary else s.textDim,
                    fontSize = 13.sp, fontFamily = s.fontFamily,
                    textAlign = TextAlign.Center, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                if (row.app != null) {
                    Text(
                        text = "Очистить", color = s.textDim, fontSize = 11.sp,
                        fontFamily = s.fontFamily,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(s.buttonCorner))
                            .clickable { onClear(row.slot) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ──────────────────────────── Вкладка «Экран» ───────────────────────────────

@Composable
private fun ScreenTab(
    keepScreenOn: Boolean, onKeepScreenOn: (Boolean) -> Unit,
    immersive: Boolean, onImmersive: (Boolean) -> Unit,
    showSpeed: Boolean, onShowSpeed: (Boolean) -> Unit,
    nightMode: Boolean, onNightMode: (Boolean) -> Unit,
    hasWallpaper: Boolean, onPickWallpaper: () -> Unit, onClearWallpaper: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SettingTile(
                icon = Icons.Rounded.Brightness6,
                title = "Не гасить экран",
                subtitle = if (keepScreenOn) "Включено" else "Выключено",
                accentIcon = keepScreenOn,
                trailing = { ThemedSwitch(keepScreenOn, onKeepScreenOn) },
                onClick = { onKeepScreenOn(!keepScreenOn) },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            SettingTile(
                icon = Icons.Rounded.Fullscreen,
                title = "Полный экран",
                subtitle = if (immersive) "Панели скрыты" else "Панели видны",
                accentIcon = immersive,
                trailing = { ThemedSwitch(immersive, onImmersive) },
                onClick = { onImmersive(!immersive) },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            SettingTile(
                icon = Icons.Rounded.Speed,
                title = "Спидометр",
                subtitle = if (showSpeed) "Показан" else "Скрыт",
                accentIcon = showSpeed,
                trailing = { ThemedSwitch(showSpeed, onShowSpeed) },
                onClick = { onShowSpeed(!showSpeed) },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            SettingTile(
                icon = Icons.Rounded.DarkMode,
                title = "Ночной режим",
                subtitle = if (nightMode) "Притухает после заката" else "Выключен",
                accentIcon = nightMode,
                trailing = { ThemedSwitch(nightMode, onNightMode) },
                onClick = { onNightMode(!nightMode) },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            SettingTile(
                icon = Icons.Rounded.Wallpaper,
                title = "Обои",
                subtitle = if (hasWallpaper) "Своя картинка" else "Градиент темы",
                accentIcon = hasWallpaper,
                onClick = { if (hasWallpaper) onClearWallpaper() else onPickWallpaper() },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
    }
}

// ─────────────────────────── Вкладка «Система» ──────────────────────────────

@Composable
private fun SystemTab(
    hasNotificationAccess: Boolean,
    onNotificationAccess: () -> Unit,
    btAutoPlay: Boolean,
    onBtAutoPlay: (Boolean) -> Unit,
    onPickBtApp: () -> Unit,
    onOpenCarSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    revision: Int,
    speedMode: String,
    onSpeedMode: (String) -> Unit,
    onReset: () -> Unit
) {
    var confirmReset by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SettingTile(
                icon = Icons.Rounded.Notifications,
                title = "Доступ к уведомлениям",
                subtitle = if (hasNotificationAccess) "Выдан" else "Не выдан",
                accentIcon = hasNotificationAccess,
                onClick = onNotificationAccess,
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            // Показывает, что именно система разрешила. Без него
            // «не работает встраивание» — слишком общая жалоба:
            // причин четыре, а снаружи выглядят одинаково.
            SettingTile(
                icon = Icons.Rounded.FactCheck,
                title = "Диагностика прав",
                subtitle = "Почему карта не в карточке",
                accentIcon = true,
                onClick = onOpenDiagnostics,
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            SettingTile(
                icon = Icons.Rounded.DirectionsCar,
                title = "Автомобиль и звук",
                subtitle = "Кнопки руля, громкость, яркость",
                accentIcon = true,
                onClick = onOpenCarSettings,
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            SettingTile(
                icon = Icons.Rounded.Bluetooth,
                title = "Музыка при подключении",
                subtitle = if (btAutoPlay) "Включается автоматически" else "Выключено",
                accentIcon = btAutoPlay,
                trailing = { ThemedSwitch(btAutoPlay, onBtAutoPlay) },
                onClick = { onBtAutoPlay(!btAutoPlay) },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            // Пакет BT-приложения у каждого производителя свой, угадать
            // его списком получается не всегда. Проще дать выбрать руками
            // один раз — дальше лаунчер поднимает канал именно им.
            val ctx = LocalContext.current
            val savedPkg = remember(revision) {
                runCatching {
                    ctx.getSharedPreferences("car_launcher_shortcuts", android.content.Context.MODE_PRIVATE)
                        .getString(BtMusicStarter.KEY_BT_APP, null)
                }.getOrNull()
            }
            SettingTile(
                icon = Icons.Rounded.LibraryMusic,
                title = "Приложение BT-музыки",
                subtitle = savedPkg?.let { pkg ->
                    runCatching {
                        ctx.packageManager.getApplicationLabel(
                            ctx.packageManager.getApplicationInfo(pkg, 0)
                        ).toString()
                    }.getOrDefault(pkg)
                } ?: "Определяется автоматически",
                accentIcon = savedPkg != null,
                onClick = onPickBtApp,
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            SettingTile(
                icon = Icons.Rounded.VerticalSplit,
                title = "Карточка спидометра",
                subtitle = when (speedMode) {
                    "freeform" -> "Приложение в карточке"
                    "split" -> "Разделённый экран"
                    else -> "На весь экран"
                },
                accentIcon = speedMode != "full",
                onClick = {
                    // Перебираем режимы по кругу
                    onSpeedMode(
                        when (speedMode) {
                            "freeform" -> "split"
                            "split" -> "full"
                            else -> "freeform"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            val pw = SettingsStore.prewarmWindow.value
            SettingTile(
                icon = Icons.Rounded.OpenInFull,
                title = "Окно: видео и карты",
                subtitle = if (pw) "Сначала на весь экран, потом в окно"
                else "Сразу в окно (может быть пусто)",
                accentIcon = pw,
                trailing = { ThemedSwitch(pw) { SettingsStore.setPrewarm(it) } },
                onClick = { SettingsStore.setPrewarm(!pw) },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            val ctx2 = LocalContext.current
            val store = remember { com.example.carlauncher.data.ShortcutStore(ctx2) }
            var count by remember { mutableStateOf(store.hiddenApps().size) }
            SettingTile(
                icon = Icons.Rounded.VisibilityOff,
                title = "Скрытые приложения",
                subtitle = if (count == 0) "Нет скрытых"
                else "Скрыто: $count · нажмите, чтобы вернуть",
                accentIcon = count > 0,
                onClick = {
                    if (count > 0) { store.unhideAll(); count = 0 }
                },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            SettingTile(
                icon = Icons.Rounded.RestartAlt,
                title = if (confirmReset) "Точно сбросить?" else "Сброс настроек",
                subtitle = if (confirmReset) "Нажмите ещё раз" else "Всё к умолчанию",
                accentIcon = confirmReset,
                onClick = {
                    if (confirmReset) { onReset(); confirmReset = false } else confirmReset = true
                },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            // Диагностика: какие датчики движения есть на этом ГУ.
            // От этого зависит, сможет ли машина на карточке крениться
            // в поворотах или будет только покачиваться от скорости.
            val ctx = LocalContext.current
            var showSensors by remember { mutableStateOf(false) }
            val sensors = remember { MotionSensors.describe(ctx) }
            val hasAccel = remember { MotionSensors.hasAccelerometer(ctx) }

            SettingTile(
                icon = Icons.Rounded.Speed,
                title = "Датчики движения",
                subtitle = if (showSensors) sensors.joinToString("\n")
                else if (hasAccel) "Акселерометр есть — нажмите"
                else "Нет акселерометра — нажмите",
                accentIcon = hasAccel,
                onClick = { showSensors = !showSensors },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            val ctx3 = LocalContext.current
            val scope3 = rememberCoroutineScope()
            var state by remember { mutableStateOf("idle") }
            var found by remember { mutableStateOf<UpdateChecker.Release?>(null) }
            var pct by remember { mutableStateOf(0f) }
            var problem by remember { mutableStateOf("") }

            SettingTile(
                icon = Icons.Rounded.Refresh,
                title = "Обновление",
                subtitle = when (state) {
                    "checking" -> "Проверяю…"
                    "none" -> "Установлена последняя версия"
                    "found" -> "Версия ${found?.version} · нажмите, чтобы скачать"
                    "downloading" -> "Скачиваю ${(pct * 100).toInt()}%"
                    "error" -> "Нет связи с сервером"
                    "mismatch" -> problem
                    else -> "Версия ${UpdateChecker.currentVersion(ctx3)} · проверить"
                },
                accentIcon = state == "found",
                onClick = {
                    when (state) {
                        "found" -> {
                            val rel = found ?: return@SettingTile
                            state = "downloading"
                            scope3.launch {
                                // Подпись сверяется до запуска установщика:
                                // иначе система выдаёт «Приложение не установлено»
                                // без объяснения причины.
                                when (val r = UpdateChecker.prepare(ctx3, rel) { pct = it }) {
                                    is UpdateChecker.Prepared.Ready -> {
                                        UpdateChecker.install(ctx3, r.apk)
                                        state = "idle"
                                    }
                                    is UpdateChecker.Prepared.WrongSignature -> {
                                        problem = "В релизе нет сборки с вашей подписью " +
                                            "(${r.expected}). Установите APK вручную"
                                        state = "mismatch"
                                    }
                                    UpdateChecker.Prepared.Failed -> state = "error"
                                }
                            }
                        }
                        "checking", "downloading" -> Unit
                        else -> {
                            state = "checking"
                            scope3.launch {
                                val rel = UpdateChecker.check(ctx3)
                                val cur = UpdateChecker.currentVersion(ctx3)
                                state = when {
                                    rel == null -> "error"
                                    UpdateChecker.isNewer(rel.version, cur) -> {
                                        found = rel; "found"
                                    }
                                    else -> "none"
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
        item {
            // Версия читается из пакета, а не пишется руками: строка
            // «1.0» висела здесь начиная с первой сборки и врала
            // пользователю на каждом обновлении.
            val ctxAbout = LocalContext.current
            val version = remember { UpdateChecker.currentVersion(ctxAbout) }
            val build = remember {
                com.example.carlauncher.data.BuildIdentity.current(ctxAbout).title
            }
            SettingTile(
                icon = Icons.Rounded.Info,
                title = "KINGSAID",
                subtitle = "Версия $version\n$build",
                onClick = { },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
    }
}

@Composable
internal fun ThemedSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val s = LocalThemeSpec.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = s.onAccent,
            checkedTrackColor = s.accent,
            uncheckedThumbColor = s.textSecondary,
            uncheckedTrackColor = s.textPrimary.copy(alpha = 0.12f),
            uncheckedBorderColor = Color.Transparent
        )
    )
}
