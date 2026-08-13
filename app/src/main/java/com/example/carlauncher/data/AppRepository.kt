package com.example.carlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast

/** Одно установленное приложение. */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable?
)

object AppRepository {

    /** Список всех приложений с иконкой в лаунчере, отсортированный по имени. */
    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        val self = context.packageName

        return resolved
            .asSequence()
            .filter { it.activityInfo.packageName != self }
            .map {
                AppInfo(
                    label = it.loadLabel(pm)?.toString().orEmpty(),
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name,
                    icon = runCatching { it.loadIcon(pm) }.getOrNull()
                )
            }
            .filter { it.label.isNotBlank() }
            .distinctBy { it.packageName + "/" + it.activityName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Запуск конкретной активности. */
    fun launch(context: Context, app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            launchPackage(context, app.packageName)
        }
    }

    /** Запуск по имени пакета. true — если удалось. */
    fun launchPackage(context: Context, packageName: String): Boolean {
        // Для карт и навигаторов открываем сразу карту, а не стартовый экран
        val intent = AppIntents.bestIntent(context, packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent); true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Ищет установленное приложение по названию.
     *
     * Списки пакетов не спасают: у каждого производителя штатные
     * приложения называются по-своему, а на одном ГУ их может быть
     * несколько с похожими именами. Зато подпись под иконкой у всех
     * человеческая — «FM-радио», «Car Info», «DSP». По ней и ищем,
     * когда точный пакет не нашёлся.
     */
    fun findByLabel(context: Context, vararg keywords: String): String? {
        val apps = runCatching { loadApps(context) }.getOrDefault(emptyList())
        for (kw in keywords) {
            val k = kw.lowercase()
            // Сначала точное совпадение, потом вхождение — иначе
            // «Камера» поймает «Камера заднего вида» раньше нужного.
            apps.firstOrNull { it.label.lowercase() == k }?.let { return it.packageName }
        }
        for (kw in keywords) {
            val k = kw.lowercase()
            apps.firstOrNull { it.label.lowercase().contains(k) }?.let { return it.packageName }
        }
        return null
    }

    /**
     * Пробует по очереди список кандидатов-пакетов, затем системный intent.
     * Так покрываются разные прошивки магнитол, где радио/BT называются по-разному.
     */
    fun launchFirstAvailable(
        context: Context,
        candidates: List<String>,
        fallback: Intent? = null,
        errorText: String = "Приложение не найдено",
        /** Слова из подписи под иконкой — запасной поиск, если пакет не угадали. */
        labels: List<String> = emptyList()
    ) {
        for (pkg in candidates) {
            if (launchPackage(context, pkg)) return
        }
        // Пакет не подошёл — ищем по подписи под иконкой. Метки берём
        // по самому списку кандидатов, поэтому запасной поиск работает
        // во всех местах сразу, включая голосовые команды.
        val effective = labels.ifEmpty { labelsFor(candidates) }
        if (effective.isNotEmpty()) {
            findByLabel(context, *effective.toTypedArray())?.let {
                if (launchPackage(context, it)) return
            }
        }
        if (fallback != null) {
            try {
                context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) { /* ignore */ }
        }
        Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show()
    }

    // --- Кандидаты для типовых китайских головных устройств ---

    val RADIO = listOf(
        "com.hzbhd.radio", "com.android.fmradio", "com.tw.radio", "com.ts.fmradio",
        "com.zhuoyi.fmradio", "com.microntek.radio", "com.hct.radio", "com.autoradio.fm",
        "com.syu.radio", "com.hsae.radio"
    )

    val MUSIC = listOf(
        "ru.yandex.music", "com.spotify.music", "com.vkontakte.android.music",
        "com.google.android.apps.youtube.music", "deezer.android.app",
        "com.hzbhd.music", "com.android.music", "com.tw.music", "com.syu.music"
    )

    val NAVIGATION = listOf(
        "ru.yandex.yandexnavi", "ru.yandex.yandexmaps", "com.google.android.apps.maps",
        "com.waze", "ru.dublgis.dgismobile", "cityguide.probki.net",
        "com.navitel", "com.sygic.aura"
    )

    val PHONE = listOf(
        "com.hzbhd.bt", "com.microntek.bluetooth", "com.syu.bt", "com.ts.bluetooth",
        "com.android.dialer", "com.google.android.dialer"
    )

    val VIDEO = listOf(
        "com.hzbhd.video", "com.android.gallery3d", "com.tw.video", "com.syu.video",
        "org.videolan.vlc", "com.mxtech.videoplayer.ad"
    )

    val CLIMATE = listOf(
        "com.hzbhd.aircondition", "com.microntek.aircondition", "com.syu.aircondition",
        "com.ts.airconditioner", "com.android.car.climate"
    )

    val CAR_INFO = listOf(
        "com.hzbhd.carinfo", "com.microntek.carinfo", "com.syu.canbus",
        "com.ts.carsetting", "com.android.car.settings"
    )

    // --- Подписи под иконками штатных приложений ---
    // Пакеты у каждого производителя свои, а названия почти одинаковые.
    // Списки собраны по реальным ГУ, включая CC3: AC, Car Info, DSP,
    // FM-радио, BT Музыка, TPMS, Fan Control, Camera.

    val RADIO_LABELS = listOf("FM-радио", "FM Radio", "Радио", "Radio", "FM")
    val MUSIC_LABELS = listOf("Музыка", "Music", "Медиа", "Media")
    val PHONE_LABELS = listOf("Bluetooth", "BT телефон", "Телефон", "Phone", "Блютуз")
    val BT_MUSIC_LABELS = listOf("BT Музыка", "Bluetooth Music", "BT Music")
    val VIDEO_LABELS = listOf("Видео", "Video", "Галерея", "Gallery")
    val CLIMATE_LABELS = listOf("AC", "Климат", "Climate", "Кондиционер", "Air")
    val CAR_INFO_LABELS = listOf("Car Info", "Car Setup", "Автомобиль", "Car", "Инфо")
    val DSP_LABELS = listOf("DSP", "Эквалайзер", "Equalizer", "EQ")
    val TPMS_LABELS = listOf("TPMS", "Давление")
    val CAMERA_LABELS = listOf("Camera", "Камера", "DVR", "Регистратор")
    val FAN_LABELS = listOf("Fan Control", "CPU Fan", "Вентилятор", "Fan")

    /** Какие подписи искать для известного списка пакетов. */
    private fun labelsFor(candidates: List<String>): List<String> = when (candidates) {
        RADIO -> RADIO_LABELS
        MUSIC -> MUSIC_LABELS
        PHONE -> PHONE_LABELS
        VIDEO -> VIDEO_LABELS
        CLIMATE -> CLIMATE_LABELS
        CAR_INFO -> CAR_INFO_LABELS
        NAVIGATION -> listOf("Навигатор", "Карты", "Maps", "Navi")
        else -> emptyList()
    }

    fun galleryFallback(): Intent =
        Intent(Intent.ACTION_VIEW, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

    fun dialerFallback(): Intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))

    fun settingsIntent(): Intent = Intent(android.provider.Settings.ACTION_SETTINGS)

    /** Экран «О приложении» в системных настройках. */
    fun openAppInfo(context: Context, packageName: String) {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * Запрос на удаление приложения. Системные удалить нельзя —
     * в этом случае открываем «О приложении», где есть «Отключить».
     */
    fun requestUninstall(context: Context, packageName: String) {
        if (isSystemApp(context, packageName)) {
            openAppInfo(context, packageName)
            return
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { openAppInfo(context, packageName) }
    }

    fun isSystemApp(context: Context, packageName: String): Boolean = runCatching {
        val flags = context.packageManager.getApplicationInfo(packageName, 0).flags
        (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    }.getOrDefault(false)
}
