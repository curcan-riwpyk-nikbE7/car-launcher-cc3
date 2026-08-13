package com.example.carlauncher.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Скачивание модели распознавания прямо из лаунчера.
 *
 * Зачем: модель весит 88 МБ и в APK её держать неудобно — файл раздувается,
 * а обновлять лаунчер приходится целиком. Проще один раз скачать по Wi-Fi
 * и хранить во внутренней памяти: дальше помощник работает офлайн навсегда.
 */
object ModelDownloader {

    private const val TAG = "ModelDownloader"

    /** Официальная маленькая русская модель Vosk, 44 МБ архивом. */
    private const val URL_RU = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"

    /** Куда распаковываем. Совпадает с тем, где VoiceEngine ищет модель. */
    fun modelDir(context: Context) = File(context.filesDir, "vosk-model-ru")

    fun isInstalled(context: Context): Boolean =
        File(modelDir(context), "am/final.mdl").exists()

    /**
     * Качает и распаковывает. Прогресс — доля от 0 до 1.
     *
     * @return true, если модель на месте и готова к работе.
     */
    suspend fun download(
        context: Context,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (isInstalled(context)) return@withContext true

        val target = modelDir(context)
        val tmpZip = File(context.cacheDir, "model-ru.zip")

        try {
            // --- качаем ---
            val conn = (URL(URL_RU).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = true
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                Log.e(TAG, "HTTP ${conn.responseCode}")
                return@withContext false
            }

            val total = conn.contentLength.toLong().coerceAtLeast(1L)
            var read = 0L
            conn.inputStream.use { input ->
                tmpZip.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        // 0..0.85 — загрузка, остаток оставляем распаковке
                        onProgress((read.toFloat() / total * 0.85f).coerceIn(0f, 0.85f))
                    }
                }
            }

            // --- распаковываем ---
            if (target.exists()) target.deleteRecursively()
            target.mkdirs()
            unzipFlattened(tmpZip, target)
            onProgress(1f)

            isInstalled(context)
        } catch (t: Throwable) {
            Log.e(TAG, "Не удалось скачать модель", t)
            runCatching { target.deleteRecursively() }
            false
        } finally {
            runCatching { tmpZip.delete() }
        }
    }

    /**
     * Распаковка со «срезанием» верхней папки.
     *
     * В архиве всё лежит внутри каталога vosk-model-small-ru-0.22/, а Vosk
     * ждёт am/ и conf/ прямо в корне указанной папки. Поэтому первый
     * сегмент пути отбрасываем.
     */
    private fun unzipFlattened(zip: File, dest: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val rel = entry.name.substringAfter('/', "")
                if (rel.isBlank()) {
                    zis.closeEntry()
                    continue
                }

                val out = File(dest, rel)
                // Защита от путей вида ../../ в архиве
                if (!out.canonicalPath.startsWith(dest.canonicalPath)) {
                    zis.closeEntry()
                    continue
                }

                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
        }
    }
}
