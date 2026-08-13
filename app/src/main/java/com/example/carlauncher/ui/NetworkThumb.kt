package com.example.carlauncher.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Картинка из сети с кэшем на диске.
 *
 * Своя реализация вместо Coil или Glide: обложки — единственное место,
 * где вообще нужна загрузка картинок, а библиотека утянула бы в APK
 * пару мегабайт и десятки классов ради трёх экранов.
 *
 * Кэш обычный файловый, без ограничения размера: обложек столько же,
 * сколько видео в списке, разрастаться нечему.
 */
@Composable
fun NetworkThumb(url: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        image = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
                val file = File(dir, md5(url))

                if (!file.exists()) {
                    val c = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("User-Agent", "CarLauncher")
                    }
                    if (c.responseCode !in 200..299) return@runCatching null
                    c.inputStream.use { input ->
                        file.outputStream().use { out -> input.copyTo(out) }
                    }
                }
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
    }

    Box(modifier = modifier.background(Color(0xFF232847))) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun md5(s: String): String =
    MessageDigest.getInstance("MD5").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }
