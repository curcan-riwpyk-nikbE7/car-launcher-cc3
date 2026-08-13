package com.example.carlauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/** Конвертирует любой Drawable (в т.ч. AdaptiveIcon) в ImageBitmap. */
fun Drawable.toImageBitmapSafe(size: Int = 144): ImageBitmap? = try {
    if (this is BitmapDrawable && bitmap != null) {
        bitmap.asImageBitmap()
    } else {
        val w = if (intrinsicWidth > 0) intrinsicWidth else size
        val h = if (intrinsicHeight > 0) intrinsicHeight else size
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp.asImageBitmap()
    }
} catch (e: Throwable) {
    null
}

/** Иконка приложения с запасным вариантом, если Drawable не отрисовался. */
@Composable
fun AppIcon(drawable: Drawable?, contentDescription: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(drawable) { drawable?.toImageBitmapSafe() }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Icon(
            imageVector = Icons.Rounded.Android,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = TextSecondary
        )
    }
}
