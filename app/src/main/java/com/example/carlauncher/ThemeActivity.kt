package com.example.carlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import com.example.carlauncher.data.ImmersiveMode
import com.example.carlauncher.data.SettingsStore
import com.example.carlauncher.data.ThemeStore
import com.example.carlauncher.ui.CarLauncherTheme
import com.example.carlauncher.ui.ThemePickerScreen
import com.example.carlauncher.ui.themeById

/**
 * Отдельное полноэкранное приложение «Темы оформления CC3».
 * Зарегистрировано в манифесте как независимое приложение с категорией LAUNCHER,
 * а также доступно в пикере приложений и в левой сетке рабочего стола.
 */
class ThemeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeStore.init(this)
        SettingsStore.init(this)
        ImmersiveMode.applyFromSettings(this)

        setContent {
            val themeId by ThemeStore.current
            CarLauncherTheme(themeById(themeId)) {
                ThemePickerScreen(
                    currentId = themeId,
                    onPick = { newTheme ->
                        ThemeStore.set(newTheme)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ImmersiveMode.applyFromSettings(this)
    }
}
