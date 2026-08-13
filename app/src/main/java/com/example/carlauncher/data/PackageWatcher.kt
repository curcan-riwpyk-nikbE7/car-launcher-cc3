package com.example.carlauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Следит за установкой и удалением приложений.
 *
 * Раньше список обновлялся только в onResume — поставил приложение,
 * а в лаунчере его нет, пока не переключишься туда-обратно.
 * Теперь система сама сообщает об изменениях.
 */
@Composable
fun PackageChangeEffect(onChanged: () -> Unit) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onChanged)

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                // Замена пакета приходит парой событий REMOVED+ADDED —
                // промежуточное с EXTRA_REPLACING пропускаем, чтобы не дёргаться дважды.
                val replacing = intent?.getBooleanExtra(Intent.EXTRA_REPLACING, false) ?: false
                if (intent?.action == Intent.ACTION_PACKAGE_REMOVED && replacing) return
                callback()
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        runCatching { context.registerReceiver(receiver, filter) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
}
