package com.example.carlauncher.data

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * Перенос уже запущенной задачи на виртуальный дисплей.
 *
 * Зачем понадобилось. Мы пытались открыть приложение сразу на нужном
 * дисплее: создавали VirtualDisplay и передавали его идентификатор
 * через ActivityOptions.setLaunchDisplayId. На этом головном устройстве
 * система такой запуск молча игнорировала — приложение открывалось
 * на основном экране, а в карточке оставался спидометр. Ни ошибки,
 * ни исключения.
 *
 * Разбор чужого лаунчера с работающими окнами показал другой подход:
 * приложение сначала запускается обычным способом, а потом его задача
 * ПЕРЕНОСИТСЯ на виртуальный дисплей. Там, где прямой запуск закрыт,
 * перенос проходит.
 *
 * Все методы скрытые, поэтому вызываются рефлексией и каждый шаг
 * проверяется отдельно: на разных прошивках отваливаются разные звенья,
 * и по логу должно быть видно, какое именно.
 */
object TaskMover {

    private const val TAG = "TaskMover"

    /** Режимы окна из WindowConfiguration — константы скрыты. */
    private const val WINDOWING_MODE_FULLSCREEN = 1
    private const val WINDOWING_MODE_FREEFORM = 5

    /**
     * Переносит задачу приложения на дисплей.
     *
     * @return true, если хотя бы один способ сработал
     */
    fun moveToDisplay(context: Context, packageName: String, displayId: Int): Boolean {
        val taskId = findTaskId(context, packageName)
        if (taskId < 0) {
            Log.w(TAG, "Задача $packageName не найдена")
            return false
        }

        // Android не держит одну задачу на двух дисплеях сразу. Если
        // приложение уже висит на основном экране, перенос молча
        // провалится — поэтому сначала убираем лишнее.
        removeTasksOnOtherDisplays(context, packageName, displayId)

        var moved = moveRootTaskToDisplay(taskId, displayId)
        if (!moved) moved = moveTaskToDisplayLegacy(context, taskId, displayId)

        if (moved) {
            // Без явного режима окно может остаться свёрнутым в стек
            // основного экрана: задача переехала, а видно её не будет.
            setWindowingMode(taskId, WINDOWING_MODE_FULLSCREEN)
            moveToFront(context, taskId)
        }
        return moved
    }

    /** Идентификатор верхней задачи приложения. */
    private fun findTaskId(context: Context, packageName: String): Int = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // getAppTasks отдаёт только свои задачи, поэтому идём через
        // getRunningTasks: он ограничен, но с REAL_GET_TASKS отдаёт всё.
        @Suppress("DEPRECATION")
        val tasks = am.getRunningTasks(50)
        tasks.firstOrNull { it.baseActivity?.packageName == packageName }?.id ?: -1
    }.getOrDefault(-1)

    /**
     * Основной путь: moveRootTaskToDisplay у ActivityTaskManager.
     *
     * Появился в Android 10 вместе с разделением сервисов; до этого
     * тем же занимался ActivityManager.moveStackToDisplay.
     */
    private fun moveRootTaskToDisplay(taskId: Int, displayId: Int): Boolean = runCatching {
        val atmClass = Class.forName("android.app.ActivityTaskManager")
        val getService = atmClass.getMethod("getService")
        val atm = getService.invoke(null)

        val m = atm.javaClass.getMethod(
            "moveRootTaskToDisplay",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        m.invoke(atm, taskId, displayId)
        true
    }.getOrElse {
        Log.w(TAG, "moveRootTaskToDisplay не прошёл: ${it.message}")
        false
    }

    /** Запасной путь для Android 9 и старше. */
    private fun moveTaskToDisplayLegacy(context: Context, taskId: Int, displayId: Int): Boolean =
        runCatching {
            val amClass = Class.forName("android.app.ActivityManagerNative")
            val getDefault = amClass.getMethod("getDefault")
            val am = getDefault.invoke(null)
            val m = am.javaClass.getMethod(
                "moveStackToDisplay",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            m.invoke(am, taskId, displayId)
            true
        }.getOrElse {
            Log.w(TAG, "moveStackToDisplay не прошёл: ${it.message}")
            false
        }

    /** Режим окна: полноэкранный внутри своего дисплея или свободный. */
    private fun setWindowingMode(taskId: Int, mode: Int): Boolean = runCatching {
        val atmClass = Class.forName("android.app.ActivityTaskManager")
        val atm = atmClass.getMethod("getService").invoke(null)
        val m = atm.javaClass.getMethod(
            "setTaskWindowingMode",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        m.invoke(atm, taskId, mode, false)
        true
    }.getOrElse {
        Log.w(TAG, "setTaskWindowingMode не прошёл: ${it.message}")
        false
    }

    /** Поднять задачу наверх, иначе она останется за спидометром. */
    private fun moveToFront(context: Context, taskId: Int): Boolean = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        am.moveTaskToFront(taskId, 0)
        true
    }.getOrElse {
        Log.w(TAG, "moveTaskToFront не прошёл: ${it.message}")
        false
    }

    /**
     * Убирает задачи приложения со всех дисплеев, кроме целевого.
     *
     * Без этого перенос проваливается: система видит, что задача уже
     * где-то есть, и отказывается её дублировать.
     */
    private fun removeTasksOnOtherDisplays(
        context: Context,
        packageName: String,
        keepDisplayId: Int
    ) {
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            am.getRunningTasks(50)
                .filter { it.baseActivity?.packageName == packageName }
                .forEach { task ->
                    val onDisplay = runCatching {
                        val f = task.javaClass.getField("displayId")
                        f.getInt(task)
                    }.getOrDefault(keepDisplayId)
                    if (onDisplay != keepDisplayId) {
                        runCatching {
                            val atm = Class.forName("android.app.ActivityTaskManager")
                                .getMethod("getService").invoke(null)
                            atm.javaClass
                                .getMethod("removeTask", Int::class.javaPrimitiveType)
                                .invoke(atm, task.id)
                        }
                    }
                }
        }.onFailure { Log.w(TAG, "Очистка задач не удалась: ${it.message}") }
    }
}
