package com.example.carlauncher.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Одно видео в списке. */
data class VideoItem(
    val id: String,
    val title: String,
    val author: String
) {
    /** Обложка отдаётся без ключа и без запроса к API. */
    val thumb: String get() = "https://img.youtube.com/vi/$id/hqdefault.jpg"
}

/**
 * Список видео для карточки.
 *
 * Ключ YouTube Data API сознательно не используем: он платный после квоты,
 * его нельзя класть в открытый репозиторий, и он привязан к аккаунту.
 * Название с автором берём через oEmbed — публичную ручку без ключа
 * и без регистрации, обложку по прямой ссылке на img.youtube.com.
 *
 * Цена решения: нельзя искать по YouTube и нельзя показать подписки.
 * Видео добавляются ссылкой, которую пользователь копирует в приложении.
 */
object VideoStore {

    private const val PREFS = "car_launcher_shortcuts"
    private const val KEY = "video_list"

    private var prefs: android.content.SharedPreferences? = null

    /** Список для UI. Меняется — экран перерисовывается сам. */
    val items = mutableStateListOf<VideoItem>()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        items.clear()
        runCatching {
            val arr = JSONArray(p.getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(
                    VideoItem(
                        id = o.getString("id"),
                        title = o.optString("title", "Видео"),
                        author = o.optString("author", "")
                    )
                )
            }
        }
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("author", it.author))
        }
        prefs?.edit()?.putString(KEY, arr.toString())?.apply()
    }

    fun remove(id: String) {
        items.removeAll { it.id == id }
        save()
    }

    fun moveUp(id: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i > 0) {
            val item = items.removeAt(i)
            items.add(i - 1, item)
            save()
        }
    }

    /**
     * Добавляет видео по ссылке.
     *
     * @return null если всё хорошо, иначе текст ошибки для показа.
     */
    suspend fun add(link: String): String? {
        val id = extractId(link) ?: return "Не похоже на ссылку YouTube"
        if (items.any { it.id == id }) return "Это видео уже в списке"

        // Название тянем сразу, чтобы в списке не висел голый идентификатор.
        // Если сети нет — добавляем всё равно, подпись подтянется позже.
        val meta = fetchMeta(id)
        items.add(
            VideoItem(
                id = id,
                title = meta?.first ?: "Видео $id",
                author = meta?.second ?: ""
            )
        )
        save()
        return null
    }

    /**
     * Вытаскивает идентификатор из любого вида ссылки: watch?v=, youtu.be,
     * shorts, embed, live. Заодно принимает голый идентификатор — если
     * человек скопировал только его.
     */
    fun extractId(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null

        // Голый id: ровно 11 символов из алфавита YouTube
        if (s.length == 11 && s.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return s

        val patterns = listOf(
            Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
            Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
            Regex("""/shorts/([A-Za-z0-9_-]{11})"""),
            Regex("""/embed/([A-Za-z0-9_-]{11})"""),
            Regex("""/live/([A-Za-z0-9_-]{11})""")
        )
        for (p in patterns) {
            p.find(s)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    /** Название и канал через oEmbed. Ключ не нужен. */
    private suspend fun fetchMeta(id: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val u = "https://www.youtube.com/oembed?url=" +
                URLEncoder.encode("https://www.youtube.com/watch?v=$id", "UTF-8") +
                "&format=json"
            val c = (URL(u).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "CarLauncher")
            }
            if (c.responseCode !in 200..299) return@runCatching null
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            o.optString("title", "Видео") to o.optString("author_name", "")
        }.getOrNull()
    }
}
