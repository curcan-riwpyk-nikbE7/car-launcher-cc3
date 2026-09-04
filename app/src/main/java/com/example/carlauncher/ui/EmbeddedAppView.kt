package com.example.carlauncher.ui

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import java.lang.reflect.Method

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
 * Касания. Штатно Android умеет доставлять касания на виртуальный
 * дисплей только с флагом VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH, который
 * появился в Android 10. На Android 8.1 (типовые ГУ на MTK/Unisoc)
 * этого нет, поэтому на старых версиях касания пробрасываем сами:
 * перехватываем MotionEvent в SurfaceView, выставляем ему displayId
 * виртуального дисплея и инжектим через InputManager.injectInputEvent.
 * Для этого нужно право INJECT_EVENTS — оно есть в сборке aospuid.
 *
 * Ограничения, о которых честно:
 *  - нужен флаг VIRTUAL_DISPLAY_FLAG_PUBLIC, приватные дисплеи чужие
 *    активности не пускают;
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
    // Есть ли право инжектить ввод (INJECT_EVENTS). Без него на Android
    // 8.1 карта показывается, но остаётся «немой» — это особенность
    // сборки без системных прав, а не поломка.
    val canInject = remember {
        context.checkSelfPermission("android.permission.INJECT_EVENTS") ==
            PackageManager.PERMISSION_GRANTED
    }

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
                    // Все касания по области приложения забираем себе:
                    // на Android 10+ их дублирует сама система, на 8.1
                    // пересылаем приложению сами (см. handleTouch).
                    setOnTouchListener { _, ev ->
                        holder.handleTouch(ev, canInject)
                    }
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var moveRunnable: Runnable? = null

    companion object {
        private const val TAG = "EmbeddedApp"

        /** Пауза перед первой попыткой переноса задачи. */
        private const val MOVE_FIRST_DELAY_MS = 700L

        /** Пауза между повторными попытками. */
        private const val MOVE_RETRY_DELAY_MS = 500L

        /** Сколько всего раз пробуем перенести задачу (~5 секунд окна). */
        private const val MAX_MOVE_ATTEMPTS = 10
    }

    fun addCallback(cb: SurfaceHolder.Callback) {
        callbacks.add(cb)
    }

    /** Кэш рефлексии для MotionEvent.setDisplayId. */
    private var setDisplayIdMethod: Method? = null

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
            // TRUSTED (0x8): дисплей, которому SurfaceFlinger доверяет
            // чужие окна. Без него (и права ADD_TRUSTED_DISPLAY) часть
            // прошивок молча не пускает приложения на виртуальный
            // дисплей — код отрабатывает, а карточка остаётся чёрной.
            // Флаг константой: в SDK он скрыт (android.view.Display).
            if (context.checkSelfPermission("android.permission.ADD_TRUSTED_DISPLAY") ==
                PackageManager.PERMISSION_GRANTED
            ) {
                flags = flags or 0x8
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
            // Одной попытки через фиксированные 900 мс мало: холодному
            // навигатору на слабом процессоре нужно несколько секунд,
            // чтобы подняться, — пробуем снова и снова, пока задача
            // не появится и не переедет (см. scheduleMove).
            scheduleMove(0)

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
        cancelMove()
        runCatching { display?.release() }
        display = null
    }

    /**
     * Перенос задачи на виртуальный дисплей с повторами.
     *
     * Попытка = найти задачу приложения (она появляется не сразу после
     * startActivity) и перетащить её на наш дисплей. Пока задача не
     * найдена или перенос не прошёл — повторяем с паузой, чтобы
     * подхватить приложение в момент готовности, а не гадать
     * «хватит ли 900 мс».
     */
    private fun scheduleMove(attempt: Int) {
        val vd = display ?: return
        val moved = TaskMover.moveToDisplay(context, packageName, vd.display.displayId)
        if (moved) {
            Log.d(TAG, "Задача $packageName на дисплее ${vd.display.displayId} " +
                "(попытка ${attempt + 1})")
            return
        }
        if (attempt + 1 >= MAX_MOVE_ATTEMPTS) {
            Log.w(TAG, "Не удалось перенести $packageName на виртуальный дисплей " +
                "за $MAX_MOVE_ATTEMPTS попыток")
            return
        }
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
     * Касания с реального экрана приходят в наше окно (мы под пальцем),
     * а приложение живёт на виртуальном дисплее. Пока система сама не
     * умеет доставлять туда тачи (Android 8.1), шлём их сами: копируем
     * событие, выставляем ему displayId виртуального дисплея и инжектим
     * через InputManager.
     *
     * @return true, если событие обработано и дальше в лаунчер идти не должно
     */
    fun handleTouch(event: MotionEvent, canInject: Boolean): Boolean {
        // Android 10+ касания на виртуальный дисплей доставляет система
        // (VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH). Нам остаётся только не
        // пускать событие в жесты лаунчера (свайпы переключения треков
        // не должны срабатывать, когда водитель двигает карту).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true

        // Права нет (сборка без INJECT_EVENTS) — не трогаем событие,
        // пусть живёт обычной жизнью Compose-тапа.
        if (!canInject) return false

        val vd = display ?: return false
        val copy = MotionEvent.obtain(event)
        try {
            if (!setDisplayId(copy, vd.display.displayId)) return false
            // Класс android.view.InputManager скрыт из новых SDK (в android.jar
            // 34 его нет), но на Android 8.1 это публичный API — зовём
            // рефлексией, как и остальные скрытые методы проекта.
            val imClass = Class.forName("android.view.InputManager")
            val im = imClass.getMethod("getInstance").invoke(null)
            val inject = imClass.getMethod(
                "injectInputEvent",
                MotionEvent::class.java,
                Int::class.javaPrimitiveType
            )
            // WAIT_FOR_FINISH: ждём доставки и спокойно утилизируем копию.
            return inject.invoke(im, copy, 2 /* INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH */) as Boolean
        } catch (e: Throwable) {
            Log.w(TAG, "Инжект касания не прошёл: ${e.message}")
            return false
        } finally {
            copy.recycle()
        }
    }

    /**
     * Выставляет событию displayId виртуального дисплея, чтобы
     * InputDispatcher отправил его окнам приложения, а не нам.
     * Метод скрытый (@hide), поэтому рефлексией, один раз.
     */
    private fun setDisplayId(ev: MotionEvent, displayId: Int): Boolean = runCatching {
        val m = setDisplayIdMethod ?: MotionEvent::class.java
            .getMethod("setDisplayId", Int::class.javaPrimitiveType)
            .also { setDisplayIdMethod = it }
        m.invoke(ev, displayId)
        true
    }.getOrElse {
        Log.w(TAG, "MotionEvent.setDisplayId недоступен: ${it.message}")
        false
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
