package com.example.carlauncher.ui

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.util.DisplayMetrics
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.carlauncher.data.AppIntents
import com.example.carlauncher.data.SystemPrivileges

/**
 * Чужое приложение, отрисованное прямо внутри карточки.
 *
 * Как это работает: создаём виртуальный дисплей, чей вывод идёт на
 * Surface нашего SurfaceView, и просим систему запустить приложение
 * именно на этом дисплее через `ActivityOptions.setLaunchDisplayId`.
 *
 * В отличие от freeform-окна здесь **нет системной рамки и заголовка** —
 * приложение рисуется как обычная вьюха лаунчера, со скруглением
 * карточки и без наложений. Именно так выглядят фирменные прошивки.
 *
 * Ограничения, о которых честно:
 *  - нужен флаг VIRTUAL_DISPLAY_FLAG_PUBLIC, приватные дисплеи чужие
 *    активности не пускают;
 *  - касания пробрасываются только если система поддерживает
 *    VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH (Android 10+, есть не везде);
 *  - часть приложений с защищённым контентом (Netflix и подобные)
 *    покажет чёрный экран — это их защита от записи.
 */
@Composable
fun EmbeddedAppView(
    packageName: String,
    modifier: Modifier = Modifier,
    onFailed: () -> Unit = {}
) {
    val context = LocalContext.current
    val s = LocalThemeSpec.current
    var failed by remember(packageName) { mutableStateOf(false) }

    // Проверяем права ДО создания дисплея. Раньше мы этого не делали и
    // получали чёрный прямоугольник: дисплей создавался, а система молча
    // отказывалась пускать на него чужую активность.
    val canEmbed = remember { SystemPrivileges.canEmbedActivities(context) }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || failed || !canEmbed) {
        FallbackNotice(onFailed, canEmbed)
        return
    }

    val holder = remember(packageName) { EmbeddedSession(context, packageName) }

    DisposableEffect(packageName) {
        onDispose { holder.release() }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(s.carCardBg)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(sh: SurfaceHolder) {
                            val ok = holder.start(
                                sh,
                                this@apply.width.coerceAtLeast(1),
                                this@apply.height.coerceAtLeast(1)
                            )
                            if (!ok) failed = true
                        }

                        override fun surfaceChanged(
                            sh: SurfaceHolder, format: Int, w: Int, h: Int
                        ) {
                            holder.resize(w, h)
                        }

                        override fun surfaceDestroyed(sh: SurfaceHolder) {
                            holder.release()
                        }
                    })
                }
            }
        )
    }
}

/**
 * Одна сессия «приложение на виртуальном дисплее».
 * Держит дисплей и умеет корректно его освобождать.
 */
private class EmbeddedSession(
    private val context: Context,
    private val packageName: String
) {
    private var display: VirtualDisplay? = null
    private var callbacks = mutableListOf<SurfaceHolder.Callback>()

    fun addCallback(cb: SurfaceHolder.Callback) {
        callbacks.add(cb)
    }

    fun start(sh: SurfaceHolder, width: Int, height: Int): Boolean {
        if (display != null) return true
        return runCatching {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val metrics = DisplayMetrics().also {
                it.densityDpi = context.resources.displayMetrics.densityDpi
            }

            // PUBLIC обязателен: на приватный дисплей чужую активность
            // система не пустит. OWN_CONTENT_ONLY не ставим — иначе
            // приложение уйдёт на основной экран.
            var flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Пробрасывает касания в приложение
                flags = flags or (1 shl 6)   // VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
            }

            val vd = dm.createVirtualDisplay(
                "CarLauncherEmbed",
                width, height, metrics.densityDpi,
                sh.surface,
                flags
            ) ?: return false

            display = vd

            val intent = AppIntents.bestIntent(context, packageName)
                ?: return false
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )

            val opts = ActivityOptions.makeBasic()
                .setLaunchDisplayId(vd.display.displayId)

            context.startActivity(intent, opts.toBundle())

            // Прямой запуск на дисплее часть прошивок игнорирует молча:
            // приложение открывается на основном экране, а в карточке
            // остаётся спидометр — ни ошибки, ни исключения.
            //
            // Поэтому вторым шагом переносим задачу принудительно.
            // Приложению нужно время подняться, иначе переносить нечего:
            // 900 мс хватает даже Картам на слабом процессоре.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                com.example.carlauncher.data.TaskMover.moveToDisplay(
                    context, packageName, vd.display.displayId
                )
            }, 900)

            true
        }.getOrDefault(false)
    }

    fun resize(w: Int, h: Int) {
        runCatching {
            display?.resize(w.coerceAtLeast(1), h.coerceAtLeast(1),
                context.resources.displayMetrics.densityDpi)
        }
    }

    fun release() {
        runCatching { display?.release() }
        display = null
    }
}

/**
 * Если встроить не вышло — сразу уходим на запасной путь (freeform),
 * не показывая пользователю чёрный экран.
 *
 * @param hadPermission были ли права вообще: если нет, это standard-сборка
 *   и текст должен объяснять причину, а не выглядеть как поломка.
 */
@Composable
private fun FallbackNotice(onFailed: () -> Unit, hadPermission: Boolean) {
    val s = LocalThemeSpec.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.Warning, null,
            tint = s.textDim, modifier = Modifier.size(26.dp)
        )
        Text(
            text = if (hadPermission)
                "Приложение не удалось встроить"
            else
                "Встраивание доступно только в системной сборке",
            color = s.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            fontFamily = s.fontFamily,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { onFailed() }
}
