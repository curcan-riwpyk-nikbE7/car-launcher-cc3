package com.example.carlauncher.ui

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
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
import com.example.carlauncher.data.TaskMover
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.reflect.Method

/**
 * Встраивание нативного приложения (Яндекс Карты, Навигатор, YouTube)
 * в карточку лаунчера по архитектуре DriveDeck (TextureView + VirtualDisplay + Async Touch).
 *
 * Особенности:
 *  - TextureView вместо SurfaceView исключает проблему черного экрана в Android 8.1;
 *  - Асинхронный проброс тачей (INJECT_INPUT_EVENT_MODE_ASYNC = 0) гарантирует отсутствие зависаний лаунчера;
 *  - Корректная матрица трансформации экранных координат в координаты виртуального экрана;
 *  - Резервный запуск через системную команду `am start --display` в IO-потоке.
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

    val canEmbed = remember {
        SystemPrivileges.canEmbedActivities(context) ||
            SystemPrivileges.isSystemUid ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || failed || !canEmbed) {
        FallbackNotice(onFailed, canEmbed)
        return
    }

    val session = remember(packageName) { EmbeddedSession(context, packageName) }

    DisposableEffect(packageName) {
        onDispose { session.release() }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(s.carCardBg)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            st: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            val ok = session.attach(this@apply, st, width, height)
                            if (!ok) failed = true
                        }

                        override fun onSurfaceTextureSizeChanged(
                            st: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            session.resize(width, height)
                        }

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            session.detach()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }

                    // Перехват и асинхронный проброс тачей в виртуальный экран
                    setOnTouchListener { v, ev ->
                        session.forwardTouch(v, ev)
                    }
                    setOnGenericMotionListener { v, ev ->
                        session.forwardTouch(v, ev)
                    }
                }
            }
        )
    }
}

/**
 * Сессия виртуального дисплея для приложения по проверенной архитектуре DriveDeck.
 */
private class EmbeddedSession(
    private val context: Context,
    private val packageName: String
) {
    private var display: VirtualDisplay? = null
    private var surface: Surface? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var moveRunnable: Runnable? = null
    private var setDisplayIdMethod: Method? = null
    private var setLaunchWindowingModeMethod: Method? = null
    private var inputManagerInstance: Any? = null
    private var injectInputEventMethod: Method? = null

    companion object {
        private const val TAG = "EmbeddedApp"
        private const val MOVE_FIRST_DELAY_MS = 600L
        private const val MOVE_RETRY_DELAY_MS = 500L
        private const val MAX_MOVE_ATTEMPTS = 8
    }

    fun attach(view: View, st: SurfaceTexture, width: Int, height: Int): Boolean {
        if (display != null) {
            resize(width, height)
            return true
        }

        return runCatching {
            val safeW = width.coerceAtLeast(1)
            val safeH = height.coerceAtLeast(1)
            st.setDefaultBufferSize(safeW, safeH)

            val s = Surface(st)
            surface = s

            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val densityDpi = context.resources.displayMetrics.densityDpi

            // DriveDeck флаги: PUBLIC (0x1) | OWN_CONTENT_ONLY / TRUSTED (0x8)
            var flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                flags = flags or (1 shl 6) // VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
            }
            if (context.checkSelfPermission("android.permission.ADD_TRUSTED_DISPLAY") ==
                PackageManager.PERMISSION_GRANTED
            ) {
                flags = flags or 0x8
            }

            val vdName = "CarLauncherEmbed-${packageName.hashCode()}"
            val vd = dm.createVirtualDisplay(
                vdName,
                safeW,
                safeH,
                densityDpi,
                s,
                flags
            ) ?: return false

            display = vd
            val displayId = vd.display.displayId
            Log.i(TAG, "Создан VirtualDisplay id=$displayId ($safeW x $safeH) для $packageName")

            // 1. Полноэкранный запуск приложения на виртуальном дисплее
            launchAppOnDisplay(displayId)

            // 2. Дополнительный перенос таска через TaskMover (как в DriveDeck restoreTask)
            scheduleMove(0)

            true
        }.getOrElse { e ->
            Log.e(TAG, "Ошибка создания VirtualDisplay для $packageName", e)
            false
        }
    }

    private fun launchAppOnDisplay(displayId: Int) {
        val intent = AppIntents.bestIntent(context, packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        val opts = ActivityOptions.makeBasic().apply {
            setLaunchDisplayId(displayId)
            // Полноэкранный режим внутри виртуального дисплея (windowingMode = 1)
            runCatching {
                val m = setLaunchWindowingModeMethod ?: ActivityOptions::class.java
                    .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    .also { setLaunchWindowingModeMethod = it }
                m.invoke(this, 1)
            }
        }

        try {
            context.startActivity(intent, opts.toBundle())
            Log.i(TAG, "startActivity($packageName) отправлен на display=$displayId")
        } catch (e: Throwable) {
            Log.w(TAG, "startActivity не прошел, пробуем резервный am start: ${e.message}")
            runShellLaunch(displayId, intent)
        }
    }

    private fun runShellLaunch(displayId: Int, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val comp = intent.component?.flattenToShortString() ?: packageName
                val cmd = "am start --display $displayId --windowingMode 1 -f 0x10000000 -n $comp"
                Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor()
                Log.i(TAG, "am start выполнен для $comp на display=$displayId")
            }
        }
    }

    fun resize(w: Int, h: Int) {
        val vd = display ?: return
        runCatching {
            vd.resize(
                w.coerceAtLeast(1),
                h.coerceAtLeast(1),
                context.resources.displayMetrics.densityDpi
            )
        }
    }

    fun detach() {
        cancelMove()
        runCatching {
            display?.release()
        }
        display = null
        runCatching {
            surface?.release()
        }
        surface = null
    }

    fun release() {
        detach()
    }

    private fun scheduleMove(attempt: Int) {
        val vd = display ?: return
        val moved = TaskMover.moveToDisplay(context, packageName, vd.display.displayId)
        if (moved) {
            Log.i(TAG, "Задача $packageName успешно перенесена на дисплей ${vd.display.displayId}")
            return
        }
        if (attempt + 1 >= MAX_MOVE_ATTEMPTS) return

        val delay = if (attempt == 0) MOVE_FIRST_DELAY_MS else MOVE_RETRY_DELAY_MS
        val r = Runnable { scheduleMove(attempt + 1) }
        moveRunnable = r
        mainHandler.postDelayed(r, delay)
    }

    private fun cancelMove() {
        moveRunnable?.let { mainHandler.removeCallbacks(it) }
        moveRunnable = null
    }

    /**
     * Асинхронный проброс тачей в VirtualDisplay (Архитектура DriveDeck).
     *
     * 1) Пересчитывает экранные координаты MotionEvent в локальные координаты виртуального дисплея;
     * 2) Назначает motionEvent.setDisplayId(displayId);
     * 3) Инжектит в InputManager в режиме ASYNC (0) без блокировки UI потока.
     */
    fun forwardTouch(view: View, event: MotionEvent): Boolean {
        // Запрещаем Compose-родителю перехватывать жесты свайпа при перемещении по карте
        view.parent?.requestDisallowInterceptTouchEvent(true)

        val vd = display ?: return false
        val displayId = vd.display.displayId

        val copy = MotionEvent.obtain(event)
        return try {
            // Смещение координат на позицию карточки на основном экране
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val matrix = Matrix()
            matrix.setTranslate(-loc[0].toFloat(), -loc[1].toFloat())
            copy.transform(matrix)

            // Назначаем дисплей назначения
            val m = setDisplayIdMethod ?: MotionEvent::class.java
                .getMethod("setDisplayId", Int::class.javaPrimitiveType)
                .also { setDisplayIdMethod = it }
            m.invoke(copy, displayId)

            // Получаем системный InputManager
            val im = inputManagerInstance ?: run {
                val imClass = Class.forName("android.view.InputManager")
                imClass.getMethod("getInstance").invoke(null).also {
                    inputManagerInstance = it
                }
            }
            val inject = injectInputEventMethod ?: run {
                val imClass = Class.forName("android.view.InputManager")
                imClass.getMethod(
                    "injectInputEvent",
                    MotionEvent::class.java,
                    Int::class.javaPrimitiveType
                ).also { injectInputEventMethod = it }
            }

            // РЕЖИМ 0 = INJECT_INPUT_EVENT_MODE_ASYNC!
            // В отличие от 2 (WAIT_FOR_FINISH), этот вызов НИКОГДА не блокирует
            // и не вешает UI поток лаунчера!
            inject.invoke(im, copy, 0)
            true
        } catch (e: Throwable) {
            false
        } finally {
            copy.recycle()
        }
    }
}

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
