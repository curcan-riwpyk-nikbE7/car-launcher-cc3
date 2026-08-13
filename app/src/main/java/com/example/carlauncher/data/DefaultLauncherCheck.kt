package com.example.carlauncher.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Проверка, назначен ли наш лаунчер главным экраном.
 *
 * Без этого человек ставит APK, жмёт «Домой» и попадает в старый лаунчер —
 * не понимая, что нужно что-то ещё настроить. Инструкция в README не помогает,
 * потому что её никто не читает.
 */
object DefaultLauncherCheck {

    /** true, если система запускает именно нас по кнопке «Домой». */
    fun isDefault(context: Context): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = context.packageManager.resolveActivity(
            intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        res?.activityInfo?.packageName == context.packageName
    }.getOrDefault(true)   // при ошибке молчим, чтобы не мозолить глаза

    /**
     * Открывает системный экран выбора лаунчера.
     * На части прошивок ГУ нужного экрана нет — тогда откроем общие настройки.
     */
    fun openChooser(context: Context) {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
            add(Intent(Settings.ACTION_HOME_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (intent in candidates) {
            val ok = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
            if (ok) return
        }
    }
}
