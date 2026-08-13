package com.example.carlauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.io.File

/**
 * Свои обои для главного экрана.
 *
 * Картинку копируем к себе в файлы: URI из системного пикера живёт
 * недолго, а после перезагрузки лаунчер должен уметь показать фон сам.
 */
object WallpaperStore {

    private const val PREFS = "car_launcher_shortcuts"
    private const val KEY = "key_wallpaper"
    private const val FILE = "wallpaper.jpg"
    private const val MAX_DIM = 1600

    private var prefs: android.content.SharedPreferences? = null

    /** Загруженные обои или null, если пользователь их не выбирал. */
    val bitmap: MutableState<Bitmap?> = mutableStateOf(null)

    /** Прозрачность фона под обоями, 0..1. */
    val dim: MutableState<Float> = mutableStateOf(0.35f)

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        dim.value = p.getFloat("key_wallpaper_dim", 0.35f)
        if (p.getBoolean(KEY, false)) load(context)
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    private fun load(context: Context) {
        runCatching {
            val f = file(context)
            if (f.exists()) bitmap.value = BitmapFactory.decodeFile(f.absolutePath)
        }
    }

    /** Сохраняет выбранную картинку, ужимая её под экран. */
    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val src = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return false

        // Уменьшаем: на слабом ГУ полноразмерное фото с телефона съест память
        val scale = minOf(
            1f,
            MAX_DIM.toFloat() / maxOf(src.width, src.height).toFloat()
        )
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                src, (src.width * scale).toInt(), (src.height * scale).toInt(), true
            )
        } else src

        file(context).outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        bitmap.value = scaled
        prefs?.edit()?.putBoolean(KEY, true)?.apply()
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        runCatching { file(context).delete() }
        bitmap.value = null
        prefs?.edit()?.putBoolean(KEY, false)?.apply()
    }

    fun setDim(v: Float) {
        dim.value = v
        prefs?.edit()?.putFloat("key_wallpaper_dim", v)?.apply()
    }
}
