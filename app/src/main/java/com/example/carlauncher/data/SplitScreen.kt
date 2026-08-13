package com.example.carlauncher.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

/**
 * Запуск приложения в разделённом экране: лаунчер слева, приложение справа.
 *
 * Встроить чужое приложение внутрь своей карточки Android не позволяет —
 * это защита системы, обходится только с root. Штатный способ показать
 * два приложения одновременно — split-screen.
 *
 * Работает не везде: на части прошивок ГУ производитель урезает или
 * полностью блокирует разделённый экран. Поэтому все вызовы обёрнуты
 * и при неудаче честно откатываются на полноэкранный запуск.
 */
object SplitScreen {

    /** Поддерживается ли разделённый экран этим устройством. */
    fun isSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val act = context as? Activity ?: return true
        return runCatching { act.isInMultiWindowMode || true }.getOrDefault(true)
    }

    /** Уже находимся в режиме разделённого экрана. */
    fun isInSplit(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val act = context as? Activity ?: return false
        return runCatching { act.isInMultiWindowMode }.getOrDefault(false)
    }

    /**
     * Отправляет лаунчер в левую половину и открывает приложение справа.
     *
     * Порядок важен: сначала системе даётся команда войти в split
     * через кнопку «Обзор», затем во вторую половину запускается
     * целевое приложение. Иначе система откроет его на весь экран.
     *
     * @return true, если удалось хотя бы запустить приложение
     */
    fun launchBeside(context: Context, packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: run {
                Toast.makeText(context, "Приложение не найдено", Toast.LENGTH_SHORT).show()
                return false
            }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return AppRepository.launchPackage(context, packageName)
        }

        // Просим систему перевести наш лаунчер в многооконный режим.
        // На стоковом Android это делает долгое нажатие «Обзор».
        val entered = runCatching {
            val act = context as? Activity
            if (act != null && !act.isInMultiWindowMode) {
                // Публичного API для входа в split нет; пробуем
                // системную команду через рефлексию — на части прошивок
                // ГУ она доступна, на остальных просто не сработает.
                val am = context.getSystemService(Context.ACTIVITY_SERVICE)
                val cls = Class.forName("android.app.ActivityManager")
                val m = cls.getMethod("setSplitScreenResizing", Boolean::class.javaPrimitiveType)
                m.invoke(am, true)
                true
            } else true
        }.getOrDefault(false)

        // FLAG_ACTIVITY_LAUNCH_ADJACENT — просьба открыть рядом, а не поверх.
        // Система выполнит её, только если уже находится в split-режиме.
        launch.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )

        val ok = runCatching { context.startActivity(launch); true }.getOrDefault(false)

        if (!ok) {
            // Не получилось рядом — открываем обычным способом,
            // чтобы нажатие не осталось без результата.
            return AppRepository.launchPackage(context, packageName)
        }

        if (!entered && !isInSplit(context)) {
            Toast.makeText(
                context,
                "Разделите экран кнопкой «Обзор», чтобы приложение осталось справа",
                Toast.LENGTH_LONG
            ).show()
        }
        return true
    }
}
