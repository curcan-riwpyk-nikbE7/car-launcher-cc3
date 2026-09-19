package com.example.carlauncher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WidgetItem(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appName: String,
    val previewBitmap: Bitmap?,
    val sizeSpan: String
)

/**
 * Фирменный автомобильный диалог выбора виджета в стиле Car Launcher CC3:
 * темные карточки, сетка 3 колонки, живые превьюшки и бейджики размеров.
 */
@Composable
fun WidgetPickerDialog(
    onSelectWidget: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val s = LocalThemeSpec.current
    var searchQuery by remember { mutableStateOf("") }
    var widgets by remember { mutableStateOf<List<WidgetItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val wm = AppWidgetManager.getInstance(context)
            val pm = context.packageManager
            val providers = wm.installedProviders ?: emptyList()

            val items = providers.mapNotNull { info ->
                runCatching {
                    val label = info.loadLabel(pm) ?: "Виджет"
                    val appInfo = pm.getApplicationInfo(info.provider.packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()

                    val drawable = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0)
                    val bmp = drawable?.toBitmapOrNull(width = 240, height = 140)

                    val cols = (info.minWidth / 70).coerceIn(1, 6)
                    val rows = (info.minHeight / 70).coerceIn(1, 4)
                    val sizeSpan = "${cols}×${rows}"

                    WidgetItem(
                        info = info,
                        label = label,
                        appName = appName,
                        previewBitmap = bmp,
                        sizeSpan = sizeSpan
                    )
                }.getOrNull()
            }.sortedBy { it.appName }

            widgets = items
            isLoading = false
        }
    }

    val filtered = remember(searchQuery, widgets) {
        if (searchQuery.isBlank()) widgets
        else widgets.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                    it.appName.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(480.dp)
                .clip(RoundedCornerShape(s.cardCorner))
                .background(s.cardBg)
                .border(s.strokeWidth, s.cardStroke, RoundedCornerShape(s.cardCorner))
                .padding(20.dp)
        ) {
            // Заголовок и закрытие
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Rounded.Widgets,
                    contentDescription = null,
                    tint = s.accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Выбор виджета",
                    color = s.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = s.fontFamily,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Закрыть",
                        tint = s.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Поле поиска
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Поиск виджета (например, Музыка или Карты)...",
                        color = s.textDim,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, null, tint = s.textSecondary, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.06f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = s.textPrimary,
                    unfocusedTextColor = s.textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(48.dp)
            )

            // Сетка виджетов
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Загрузка установленных виджетов...", color = s.textSecondary, fontSize = 14.sp)
                }
            } else if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Виджеты не найдены", color = s.textDim, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered) { item ->
                        WidgetCardItem(
                            item = item,
                            onClick = {
                                onSelectWidget(item.info)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCardItem(
    item: WidgetItem,
    onClick: () -> Unit
) {
    val s = LocalThemeSpec.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            if (item.previewBitmap != null) {
                Image(
                    bitmap = item.previewBitmap.asImageBitmap(),
                    contentDescription = item.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            } else {
                Icon(
                    Icons.Rounded.Widgets,
                    contentDescription = null,
                    tint = s.accent.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp)
                )
            }

            // Бейджик размера (например, 4x2)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.sizeSpan,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = item.label,
            color = s.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = s.fontFamily,
            textAlign = TextAlign.Center
        )

        Text(
            text = item.appName,
            color = s.textDim,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = s.fontFamily,
            textAlign = TextAlign.Center
        )
    }
}

private fun Drawable.toBitmapOrNull(width: Int = 240, height: Int = 140): Bitmap? {
    return runCatching {
        val w = if (intrinsicWidth > 0) intrinsicWidth else width
        val h = if (intrinsicHeight > 0) intrinsicHeight else height
        val clampedW = w.coerceIn(48, 600)
        val clampedH = h.coerceIn(48, 400)
        val bmp = Bitmap.createBitmap(clampedW, clampedH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp
    }.getOrNull()
}
