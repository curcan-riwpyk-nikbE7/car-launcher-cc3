package com.example.carlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.carlauncher.data.AppInfo
import android.widget.Toast
import com.example.carlauncher.data.AppRepository
import com.example.carlauncher.data.PackageChangeEffect
import com.example.carlauncher.data.SettingsStore
import com.example.carlauncher.data.ShortcutStore
import com.example.carlauncher.ui.AllAppsScreen
import com.example.carlauncher.data.ThemeStore
import com.example.carlauncher.ui.CarLauncherTheme
import com.example.carlauncher.ui.themeById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AllAppsActivity : ComponentActivity() {

    private fun reload() {
        lifecycleScope.launch {
            loading = true
            apps = withContext(Dispatchers.IO) { AppRepository.loadApps(this@AllAppsActivity) }
            recents = SettingsStore.recentApps()
            loading = false
        }
    }

    /** Кладёт приложение в первую свободную ячейку избранного. */
    private fun addToFirstFreeSlot(app: AppInfo) {
        val store = ShortcutStore(this)
        val slots = listOf(
            ShortcutStore.SLOT_1, ShortcutStore.SLOT_2, ShortcutStore.SLOT_3,
            ShortcutStore.SLOT_4, ShortcutStore.SLOT_5
        )
        val free = slots.firstOrNull { store.get(it) == null } ?: slots.last()
        store.set(free, app.packageName)
        Toast.makeText(this, "${app.label} добавлено на главный экран", Toast.LENGTH_SHORT).show()
    }

    private var apps by mutableStateOf<List<AppInfo>>(emptyList())
    private var loading by mutableStateOf(true)
    /** Недавно запускавшиеся приложения — для строки над сеткой. */
    private var recents by mutableStateOf<List<String>>(emptyList())

    /**
     * Система возвращает бары после диалогов выбора и системных окон,
     * поэтому режим приходится назначать заново при каждом возврате.
     */
    override fun onResume() {
        super.onResume()
        com.example.carlauncher.data.ImmersiveMode.applyFromSettings(this)
        // Вернулись из запущенного приложения — оно теперь первое в «Недавних».
        recents = SettingsStore.recentApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.init(this)
        // Без этого поверх списка висит штатная панель ГУ и полоса
        // навигации — вместе они съедают около трети экрана.
        com.example.carlauncher.data.SettingsStore.init(this)
        com.example.carlauncher.data.ImmersiveMode.applyFromSettings(this)
        setContent {
            val themeId by ThemeStore.current
            CarLauncherTheme(themeById(themeId)) {
                AllAppsScreen(
                    apps = apps,
                    loading = loading,
                    recents = recents,
                    onBack = { finish() },
                    onAddToFavorites = { app -> addToFirstFreeSlot(app) }
                )
                // Список сам обновится при установке/удалении приложения
                PackageChangeEffect { reload() }
            }
        }
        reload()
    }
}
