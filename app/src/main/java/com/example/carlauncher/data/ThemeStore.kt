package com.example.carlauncher.data

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Хранит выбранную тему и раздаёт её всему приложению.
 *
 * Это обычный синглтон с mutableStateOf: смена темы мгновенно
 * перерисовывает и главный экран, и «Все приложения», и галерею тем —
 * без перезапуска Activity.
 */
object ThemeStore {

    private const val PREFS = "car_launcher_shortcuts"

    private var prefs: android.content.SharedPreferences? = null

    /** Текущий id темы. Читается всеми экранами. */
    val current: MutableState<String> = mutableStateOf("violet")

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        current.value = p.getString(ShortcutStore.KEY_THEME, "violet") ?: "violet"
    }

    fun set(id: String) {
        current.value = id
        prefs?.edit()?.putString(ShortcutStore.KEY_THEME, id)?.apply()
    }
}
