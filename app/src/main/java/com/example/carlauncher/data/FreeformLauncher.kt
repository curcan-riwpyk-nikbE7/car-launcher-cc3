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

    /** Устройство заявляет поддержку плавающих окон. */
    fun hasFeature(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT
            )

    /** Режим включён в системных настройках (в том числе через adb). */
    fun isEnabledInSettings(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
    }.getOrDefault(false)

    /** Можно ли пытаться запускать в плавающем окне. */
    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            (hasFeature(context) || isEnabledInSettings(context))

    /**
     * Запускает приложение в окне с заданными границами.
     *
     * @param bounds прямоугольник в пикселях экрана — обычно позиция карточки
     * @return true, если запуск удался
     */
    /**
     * @param prewarm сначала запустить приложение обычным полноэкранным
     *   способом, и лишь потом переоткрыть в окне.
     *
     *   Нужно для YouTube и свежих версий Яндекс.Навигатора: они
     *   отказываются рисоваться сразу в маленьком окне и показывают
     *   пустоту. Сама TEYES боролась с этим несколькими прошивками —
     *   в истории обновлений CC3 это описано прямым текстом, а в
     *   настройках есть отдельный «второй вариант» запуска для YouTube.
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

        if (prewarm) {
            // Даём приложению стартовать нормально, а через мгновение
            // просим переехать в окно. Задержка небольшая: дольше —
            // и пользователь успеет увидеть полноэкранный интерфейс.
            AppRepository.launchPackage(context, packageName)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                launchInBounds(context, packageName, bounds, prewarm = false)
            }, 900)
            return true
        }

        // Открываем сразу карту/видео, а не домашний экран приложения
        val launch = AppIntents.bestIntent(context, packageName) ?: return false

        // MULTIPLE_TASK нужен, чтобы приложение открылось в новом окне,
        // а не переиспользовало уже существующую полноэкранную задачу.
        launch.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )

        val opts = ActivityOptions.makeBasic().apply {
            runCatching { setLaunchBounds(bounds) }
            // Просим именно плавающий режим. Константа скрыта в SDK,
            // но значение стабильно: WINDOWING_MODE_FREEFORM = 5.
            runCatching {
                val m = ActivityOptions::class.java
                    .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                m.invoke(this, 5)
            }
        }

        return runCatching {
            context.startActivity(launch, opts.toBundle())
            true
        }.getOrElse {
            // Не вышло плавающим — открываем обычным способом,
            // чтобы нажатие не осталось без результата.
            AppRepository.launchPackage(context, packageName)
        }
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
