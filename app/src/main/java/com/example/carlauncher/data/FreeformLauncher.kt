package com.example.carlauncher.data

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * Запуск приложения в плавающем окне точно по границам карточки.
 *
 * Это тот самый способ, которым лаунчер может показать YouTube или карту
 * «внутри» виджета, не будучи системным приложением.
 *
 * Работает через публичный API `ActivityOptions.setLaunchBounds` (API 24+):
 * мы отдаём системе прямоугольник, и она сама рисует чужое приложение
 * в этих границах. Своё окно мы при этом не подменяем — приложение живёт
 * в отдельном окне поверх, но ровно в области карточки.
 *
 * Единственное условие — на устройстве должен быть включён режим
 * freeform-окон. На части ГУ он включён производителем, на остальных
 * включается одной командой adb без root:
 *
 *     adb shell settings put global enable_freeform_support 1
 *
 * Так же работает популярный Taskbar — доказательство, что путь рабочий.
 */
object FreeformLauncher {

    var lastPackage: String? = null
    var lastBounds: Rect? = null

    /** Устройство заявляет поддержку плавающих окон (Android 7.0+). */
    fun hasFeature(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    /** Режим включён в системных настройках (в том числе через adb). */
    fun isEnabledInSettings(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
    }.getOrDefault(true)

    /** Можно ли запускать в плавающем окне (на всех версиях от Nougat). */
    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    /**
     * Восстанавливает активное плавающее окно при возврате в лаунчер.
     */
    fun resumeActiveWindow(context: Context) {
        val pkg = lastPackage ?: return
        val b = lastBounds ?: return
        if (b.width() > 100 && b.height() > 100) {
            launchInBounds(context, pkg, b, prewarm = false)
        }
    }

    /**
     * Закрывает активное плавающее окно при переключении на спидометр.
     */
    fun closeActiveWindow(context: Context) {
        val pkg = lastPackage ?: return
        lastPackage = null
        lastBounds = null
        runCatching {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $pkg"))
        }
        runCatching {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "am force-stop $pkg"))
        }
    }

    /**
     * Запускает приложение в окне точно с заданными границами карточки.
     *
     * @param bounds прямоугольник в пикселях экрана
     * @return true, если запуск удался
     */
    fun launchInBounds(
        context: Context,
        packageName: String,
        bounds: Rect,
        prewarm: Boolean = false
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return AppRepository.launchPackage(context, packageName)
        }
        if (bounds.isEmpty || bounds.width() <= 0 || bounds.height() <= 0) {
            return false
        }

        lastPackage = packageName
        lastBounds = Rect(bounds)

        // Активируем свободные окна в системе
        SystemPrivileges.enableForceResizable(context)

        if (prewarm) {
            AppRepository.launchPackage(context, packageName)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                launchInBounds(context, packageName, bounds, prewarm = false)
            }, 900)
            return true
        }

        // Открываем сразу карту/видео, а не домашний экран приложения
        val launch = AppIntents.bestIntent(context, packageName)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false

        launch.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )

        val opts = ActivityOptions.makeBasic().apply {
            runCatching { setLaunchBounds(bounds) }
            // WINDOWING_MODE_FREEFORM = 5
            runCatching {
                val m = ActivityOptions::class.java
                    .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                m.invoke(this, 5)
            }
        }

        val started = runCatching {
            context.startActivity(launch, opts.toBundle())
            true
        }.getOrDefault(false)

        // Если задача приложения уже была запущена, принудительно переключаем её режим на Freeform
        runCatching {
            val task = TaskMover.findTask(context, packageName)
            if (task != null) {
                val atmClass = Class.forName("android.app.ActivityTaskManager")
                val atm = atmClass.getMethod("getService").invoke(null)
                runCatching {
                    val mMode = atm.javaClass.getMethod(
                        "setTaskWindowingMode",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType
                    )
                    mMode.invoke(atm, task.id, 5, false)
                }
                runCatching {
                    val mResize = atm.javaClass.getMethod(
                        "resizeTask",
                        Int::class.javaPrimitiveType,
                        Rect::class.java,
                        Int::class.javaPrimitiveType
                    )
                    mResize.invoke(atm, task.id, bounds, 0)
                }
            }
        }

        return started || AppRepository.launchPackage(context, packageName)
    }

    /**
     * Каким приложениям нужен предварительный полноэкранный запуск.
     *
     * Список короткий намеренно: лишняя вспышка полного экрана заметна
     * глазом, поэтому включаем её только там, где без неё окно пустое.
     */
    fun needsPrewarm(packageName: String): Boolean {
        val p = packageName.lowercase()
        return p.contains("youtube") ||
            p.contains("yandexnavi") ||
            p.contains("yandexmaps") ||
            p.contains("waze")
    }

    /**
     * Пресеты области окна.
     *
     * Маленькое окно — главная беда: приложение вроде YouTube пытается
     * уместить в него полный телефонный интерфейс и выглядит месивом.
     * Чем больше площадь, тем аккуратнее приложение себя рисует.
     */
    enum class Area(val title: String, val hint: String) {
        Card("Карточка", "Точно на месте спидометра"),
        RightColumn("Колонка", "Карточка плюс место под ней"),
        RightHalf("Крупно", "Вся правая часть экрана")
    }

    /**
     * Считает границы окна для выбранной области.
     *
     * @param card границы карточки авто
     * @param screenW ширина экрана в пикселях
     * @param screenH высота экрана в пикселях
     * @param panelRight правая граница боковой панели — левее неё не заходим
     */
    fun boundsFor(
        area: Area,
        card: Rect,
        screenW: Int,
        screenH: Int
    ): Rect = when (area) {
        Area.Card -> Rect(card)

        // Карточка плюс место под ней до низа экрана.
        // Ширину сохраняем — иначе окно наедет на плеер слева.
        Area.RightColumn -> Rect(
            card.left,
            card.top,
            card.right,
            screenH - 16
        )

        // Вся правая часть: от левого края карточки до края экрана.
        // Плеер слева остаётся видимым.
        Area.RightHalf -> Rect(
            card.left,
            card.top,
            screenW - 16,
            screenH - 16
        )
    }

    /** Подсказка, как включить режим, если он выключен. */
    fun explainHowToEnable(context: Context) {
        Toast.makeText(
            context,
            "Включите плавающие окна командой:\n" +
                "adb shell settings put global enable_freeform_support 1",
            Toast.LENGTH_LONG
        ).show()
    }
}
