package com.example.carlauncher.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings

/**
 * Проверка «а мы вообще системное приложение на этом устройстве?».
 *
 * Одна и та же кодовая база собирается двумя APK: standard (обычный ключ) и
 * system (ключ platform из AOSP). Пользователь может поставить любой, поэтому
 * решать, можно ли встраивать приложение в карточку, надо в рантайме,
 * а не по BuildConfig — так standard-сборка не покажет чёрный экран,
 * а сразу уйдёт на freeform.
 */
object SystemPrivileges {

    /**
     * uid системы == 1000. Если прошивка подписана тем же ключом,
     * что и наш APK, sharedUserId сработал и мы внутри этого uid.
     */
    val isSystemUid: Boolean
        get() = Process.myUid() == 1000

    /**
     * Главная проверка. ACTIVITY_EMBEDDING — то самое право, из-за
     * отсутствия которого VirtualDisplay раньше оставался чёрным:
     * система просто не пускала чужую активность на наш дисплей.
     */
    fun canEmbedActivities(context: Context): Boolean {
        if (!isSystemUid) return false
        return context.checkPermission(
            "android.permission.ACTIVITY_EMBEDDING",
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Право менять secure-настройки. Нужно, чтобы включить
     * force_resizable_activities — иначе Карты и YouTube внутри
     * маленького окна верстаются как на полном экране.
     */
    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkPermission(
            android.Manifest.permission.WRITE_SECURE_SETTINGS,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Заставляет любые приложения корректно подстраиваться под окно
     * произвольного размера. Без этого встроенная карта обрезается.
     * Вызывать один раз при старте лаунчера.
     */
    fun enableForceResizable(context: Context): Boolean {
        if (!canWriteSecureSettings(context)) return false
        return runCatching {
            Settings.Global.putInt(
                context.contentResolver,
                "force_resizable_activities", 1
            )
            // Freeform как запасной путь: пригодится, даже когда
            // встраивание работает — для «развернуть окно».
            Settings.Global.putInt(
                context.contentResolver,
                "enable_freeform_support", 1
            )
            true
        }.getOrDefault(false)
    }

    /** Человеческое описание режима — показываем в настройках. */
    fun describe(context: Context): String = when {
        canEmbedActivities(context) -> "Системный режим: приложения открываются внутри карточки"
        isSystemUid -> "Системный uid есть, но право ACTIVITY_EMBEDDING не выдано"
        else -> "Обычный режим: приложения открываются отдельным окном"
    }
}
