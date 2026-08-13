package com.example.carlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.carlauncher.data.AppInfo
import com.example.carlauncher.data.AppRepository
import com.example.carlauncher.data.MediaControl
import com.example.carlauncher.data.SettingsStore
import com.example.carlauncher.data.ShortcutStore
import com.example.carlauncher.data.ThemeStore
import com.example.carlauncher.data.WallpaperStore
import com.example.carlauncher.ui.AppPickerDialog
import com.example.carlauncher.ui.CarLauncherTheme
import com.example.carlauncher.ui.SettingsScreen
import com.example.carlauncher.ui.ShortcutRow
import com.example.carlauncher.ui.themeById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    private var apps by mutableStateOf<List<AppInfo>>(emptyList())
    private var revision by mutableStateOf(0)
    private var notifAccess by mutableStateOf(false)

    private val slots = listOf(
        ShortcutStore.SLOT_1 to "Ячейка 1",
        ShortcutStore.SLOT_2 to "Ячейка 2",
        ShortcutStore.SLOT_3 to "Ячейка 3",
        ShortcutStore.SLOT_4 to "Ячейка 4",
        ShortcutStore.SLOT_5 to "Ячейка 5",
        ShortcutStore.SLOT_6 to "Ячейка 6",
        ShortcutStore.SLOT_7 to "Ячейка 7",
        ShortcutStore.SLOT_8 to "Ячейка 8",
        ShortcutStore.SLOT_9 to "Ячейка 9",
        ShortcutStore.SLOT_10 to "Ячейка 10"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.init(this)
        SettingsStore.init(this)
        WallpaperStore.init(this)

        val store = ShortcutStore(this)

        setContent {
            val themeId by ThemeStore.current
            var pickerSlot by remember { mutableStateOf<String?>(null) }
            // Системный пикер картинки для обоев
            val wallpaperPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    WallpaperStore.save(this@SettingsActivity, uri)
                    revision++
                }
            }

            CarLauncherTheme(themeById(themeId)) {
                val rows = remember(revision, apps) {
                    slots.map { (slot, title) ->
                        ShortcutRow(
                            slot = slot,
                            title = title,
                            app = store.get(slot)?.let { pkg ->
                                apps.firstOrNull { it.packageName == pkg }
                            }
                        )
                    }
                }

                SettingsScreen(
                    themeId = themeId,
                    onThemePick = { ThemeStore.set(it) },
                    gesturesEnabled = SettingsStore.gesturesEnabled.value,
                    onGestures = { SettingsStore.setGestures(it) },
                    swipeThreshold = SettingsStore.swipeThreshold.value,
                    onSwipe = { SettingsStore.setSwipe(it) },
                    volumeStep = SettingsStore.volumeStep.value,
                    onVolumeStep = { SettingsStore.setVolumeStep(it) },
                    haptic = SettingsStore.hapticEnabled.value,
                    onHaptic = { SettingsStore.setHaptic(it) },
                    keepScreenOn = SettingsStore.keepScreenOn.value,
                    onKeepScreenOn = { SettingsStore.setKeepScreenOn(it) },
                    immersive = SettingsStore.immersive.value,
                    onImmersive = { SettingsStore.setImmersive(it) },
                    showSpeed = SettingsStore.showSpeed.value,
                    onShowSpeed = { SettingsStore.setShowSpeed(it) },
                    nightMode = SettingsStore.nightMode.value,
                    onNightMode = { SettingsStore.setNightMode(it) },
                    btAutoPlay = SettingsStore.btAutoPlay.value,
                    onBtAutoPlay = { SettingsStore.setBtAutoPlay(it) },
                    speedMode = SettingsStore.speedMode.value,
                    onSpeedMode = { SettingsStore.setSpeedMode(it) },
                    hasWallpaper = WallpaperStore.bitmap.value != null,
                    onPickWallpaper = { wallpaperPicker.launch("image/*") },
                    onClearWallpaper = {
                        WallpaperStore.clear(this@SettingsActivity); revision++
                    },
                    shortcuts = rows,
                    onShortcutPick = { pickerSlot = it },
                    onShortcutClear = { store.clear(it); revision++ },
                    hasNotificationAccess = notifAccess,
                    onNotificationAccess = {
                        MediaControl.openNotificationAccessSettings(this@SettingsActivity)
                    },
                    onReset = {
                        SettingsStore.resetAll(this@SettingsActivity)
                        revision++
                    },
                    onBack = { finish() }
                )

                pickerSlot?.let { slot ->
                    AppPickerDialog(
                        apps = apps,
                        title = "Выберите приложение",
                        onPick = { app ->
                            store.set(slot, app.packageName)
                            revision++
                            pickerSlot = null
                        },
                        onReset = {
                            store.clear(slot)
                            revision++
                            pickerSlot = null
                        },
                        onDismiss = { pickerSlot = null }
                    )
                }
            }
        }

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        notifAccess = MediaControl.hasNotificationAccess(this)
    }

    private fun loadApps() {
        lifecycleScope.launch {
            apps = withContext(Dispatchers.IO) { AppRepository.loadApps(this@SettingsActivity) }
        }
    }
}
