package com.example.carlauncher.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.carlauncher.BuildConfig
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Проверка и установка обновлений через GitHub Releases.
 *
 * Авторизация не нужна: релизы публичного репозитория отдаются всем.
 * Токен в APK класть нельзя — его вытащит любой, кто распакует файл.
 *
 * Подпись имеет значение: обновление встанет поверх старого только если
 * подписано тем же ключом. У нас два варианта сборки (обычная и
 * системная), поэтому имя файла в релизе решает, какую скачивать.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    private const val API =
        "https://api.github.com/repos/curcan-riwpyk-nikbE7/car-launcher-cc3/releases/latest"

    /**
     * Что нашли на сервере.
     *
     * @param alternates запасные файлы того же релиза. Нужны, если
     *        основной окажется с чужой подписью: тогда перебираем их,
     *        вместо того чтобы показывать пользователю глухой отказ
     *        установщика.
     */
    data class Release(
        val version: String,
        val notes: String,
        val url: String,
        val sizeBytes: Long,
        val alternates: List<Pair<String, Long>> = emptyList()
    )

    /** Чем закончилась подготовка файла к установке. */
    sealed interface Prepared {
        /** Файл проверен, подпись совпадает — можно ставить. */
        data class Ready(val apk: File) : Prepared

        /** Ни один файл в релизе не подошёл по подписи. */
        data class WrongSignature(val expected: String, val found: String) : Prepared

        /** Скачать не удалось. */
        data object Failed : Prepared
    }

    /** Версия установленного лаунчера. */
    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    }.getOrDefault("1.0")

    /** Есть ли Wi-Fi. По мобильному 30 МБ качать не стоит без спроса. */
    fun isWifi(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)

    /**
     * Спрашивает GitHub о последнем релизе.
     * @return null, если релизов нет, сети нет или ответ не разобрался.
     */
    suspend fun check(context: Context): Release? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 12000
                // GitHub требует User-Agent, иначе отвечает 403
                setRequestProperty("User-Agent", "CarLauncher")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${conn.responseCode}")
                return@withContext null
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

            val tag = json.optString("tag_name").removePrefix("v").trim()
            if (tag.isBlank()) return@withContext null

            // Качаем APK ровно той сборки, что установлена: у наших
            // вариантов разные подписи, и Android откажется ставить
            // поверх чужую с сообщением «Приложение не установлено».
            //
            // Выбор идёт по ИМЕНИ ФАЙЛА, но имя — только подсказка,
            // а не решение. Раньше сначала искали слово "system",
            // потом точное имя из BuildConfig; оба способа ломались
            // при переименовании файлов в релизе. Поэтому здесь имя
            // задаёт лишь порядок перебора, а окончательную проверку
            // делает BuildIdentity уже по скачанному файлу: сверяет
            // подпись и системный uid с нашими собственными.
            val assets = json.optJSONArray("assets") ?: return@withContext null

            val candidates = mutableListOf<Pair<String, Long>>()
            val preferred = BuildConfig.UPDATE_ASSET.lowercase()

            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (!name.lowercase().endsWith(".apk")) continue
                val url = a.optString("browser_download_url")
                val size = a.optLong("size")
                if (url.isBlank()) continue

                // Свой файл ставим первым, остальные — запасными:
                // если его переименовали, обновление всё равно найдётся
                // перебором с проверкой подписи.
                if (name.lowercase() == preferred) {
                    candidates.add(0, url to size)
                } else {
                    candidates.add(url to size)
                }
            }

            if (candidates.isEmpty()) {
                Log.w(TAG, "В релизе нет ни одного APK")
                return@withContext null
            }

            Release(
                version = tag,
                notes = json.optString("body").take(400),
                url = candidates.first().first,
                sizeBytes = candidates.first().second,
                alternates = candidates.drop(1)
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Проверка обновления не удалась", t)
            null
        }
    }

    /**
     * Сравнение версий вида 1.2.3 по числам.
     * Строковое сравнение здесь врёт: «1.10» меньше «1.9» по алфавиту.
     */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-').mapNotNull { it.toIntOrNull() }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Качает APK во временную папку. Прогресс — доля 0..1. */
    suspend fun download(
        context: Context,
        release: Release,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "update.apk")
        try {
            val conn = (URL(release.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CarLauncher")
            }
            if (conn.responseCode !in 200..299) return@withContext null

            val total = if (release.sizeBytes > 0) release.sizeBytes
            else conn.contentLength.toLong().coerceAtLeast(1L)
            var read = 0L

            conn.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        read += n
                        onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            out
        } catch (t: Throwable) {
            Log.e(TAG, "Скачивание не удалось", t)
            runCatching { out.delete() }
            null
        }
    }

    /**
     * Качает и проверяет обновление.
     *
     * Ключевой шаг — сверка подписи ДО запуска установщика. Раньше
     * файл отдавался системе как есть, и при несовпадении подписи
     * пользователь видел «Приложение не установлено» без всяких
     * пояснений: непонятно, файл битый, места нет или ключ чужой.
     *
     * Теперь при несовпадении перебираем остальные файлы релиза —
     * возможно, нужная сборка лежит там под другим именем. И только
     * если не подошёл ни один, сообщаем причину человеческими словами.
     */
    suspend fun prepare(
        context: Context,
        release: Release,
        onProgress: (Float) -> Unit
    ): Prepared = withContext(Dispatchers.IO) {
        val mine = BuildIdentity.current(context)

        val urls = buildList {
            add(release.url to release.sizeBytes)
            addAll(release.alternates)
        }

        var lastFound = ""

        for ((index, pair) in urls.withIndex()) {
            val (url, size) = pair

            val file = download(
                context,
                release.copy(url = url, sizeBytes = size)
            ) { p ->
                // Прогресс считаем по всему перебору, иначе полоса
                // прыгала бы на ноль при каждой новой попытке.
                onProgress((index + p) / urls.size)
            } ?: continue

            val theirs = BuildIdentity.ofFile(context, file)
            if (theirs == null) {
                Log.w(TAG, "Файл не разобрался как APK")
                runCatching { file.delete() }
                continue
            }

            if (BuildIdentity.isCompatible(mine, theirs)) {
                Log.i(TAG, "Подходит: ${theirs.title}")
                onProgress(1f)
                return@withContext Prepared.Ready(file)
            }

            Log.w(TAG, "Подпись не та: нужна ${mine.short}, у файла ${theirs.short}")
            lastFound = theirs.short
            runCatching { file.delete() }
        }

        if (lastFound.isNotEmpty()) {
            Prepared.WrongSignature(expected = mine.short, found = lastFound)
        } else {
            Prepared.Failed
        }
    }

    /**
     * Отдаёт файл системному установщику.
     *
     * Напрямую путь к файлу передавать нельзя — с Android 7 система
     * это запрещает, нужен FileProvider с временным разрешением.
     */
    fun install(context: Context, apk: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apk
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { Log.e(TAG, "Установщик не открылся", it) }
    }
}
