package com.example.carlauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Виджет Android прямо в карточке.
 *
 * Это единственный законный способ показать чужое приложение внутри
 * своего окна без прав прошивки. TaskView и ACTIVITY_EMBEDDING, которыми
 * пользуется штатный лаунчер, требуют подписи ключом прошивки — на этом
 * ГУ она не подошла.
 *
 * Важное ограничение: виджет и приложение — разные вещи. Показать можно
 * только то, что разработчик приложения сам оформил как виджет. У YouTube
 * виджета нет вообще, у Яндекс.Навигатора он обычно кнопка-ярлык,
 * а не живая карта.
 */
object WidgetHost {

    private const val TAG = "WidgetHost"

    // Идентификатор хоста произвольный, важно лишь чтобы он не менялся
    // между запусками: иначе система отзовёт выданные виджеты.
    private const val HOST_ID = 0x4C41

    private var host: AppWidgetHost? = null

    fun host(context: Context): AppWidgetHost =
        host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }

    fun manager(context: Context): AppWidgetManager =
        AppWidgetManager.getInstance(context.applicationContext)

    /** Список всех виджетов, установленных на устройстве. */
    fun available(context: Context): List<AppWidgetProviderInfo> =
        runCatching { manager(context).installedProviders }.getOrDefault(emptyList())

    /**
     * Резервирует место под виджет и просит у системы разрешение.
     *
     * Часть виджетов отдаётся сразу, часть требует подтверждения
     * пользователем — тогда вернётся false и нужно показать системный
     * диалог через bindPermissionIntent.
     */
    fun allocate(context: Context, info: AppWidgetProviderInfo): Pair<Int, Boolean> {
        val id = host(context).allocateAppWidgetId()
        val ok = runCatching {
            manager(context).bindAppWidgetIdIfAllowed(id, info.provider)
        }.getOrDefault(false)
        return id to ok
    }

    fun release(context: Context, widgetId: Int) {
        runCatching { host(context).deleteAppWidgetId(widgetId) }
    }
}

/**
 * Показывает выбранный виджет.
 *
 * @param widgetId идентификатор, выданный AppWidgetHost
 */
@Composable
fun WidgetView(widgetId: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val s = LocalThemeSpec.current

    val info = remember(widgetId) {
        runCatching { WidgetHost.manager(context).getAppWidgetInfo(widgetId) }.getOrNull()
    }

    if (info == null) {
        Box(modifier = modifier.background(s.cardBg), contentAlignment = Alignment.Center) {
            Text("Виджет недоступен", color = s.textSecondary, fontSize = 15.sp)
        }
        return
    }

    // Хост должен слушать обновления, пока виджет на экране. Без
    // startListening виджет показывается, но никогда не обновляется.
    DisposableEffect(widgetId) {
        runCatching { WidgetHost.host(context).startListening() }
        onDispose { runCatching { WidgetHost.host(context).stopListening() } }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WidgetHost.host(ctx).createView(ctx, widgetId, info).apply {
                setAppWidget(widgetId, info)
            }
        },
        update = { view ->
            // Сообщаем виджету реальный размер карточки: без этого он
            // рисуется по размеру из манифеста и обрезается.
            runCatching {
                val w = with(density) { view.width.toDp().value.toInt() }
                val h = with(density) { view.height.toDp().value.toInt() }
                if (w > 0 && h > 0) {
                    view.updateAppWidgetSize(Bundle(), w, h, w, h)
                }
            }
        }
    )
}

/** Выбор виджета из установленных на устройстве. */
@Composable
fun WidgetPickerDialog(
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val s = LocalThemeSpec.current
    val widgets = remember { WidgetHost.available(context).sortedBy { it.loadLabel(context.packageManager) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp)
                .clip(RoundedCornerShape(s.cardCorner))
                .background(s.bg.first())
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Виджеты приложений",
                    color = s.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = s.fontFamily
                )
                Box(Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Закрыть",
                    tint = s.textSecondary,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onDismiss)
                        .padding(8.dp)
                )
            }

            if (widgets.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "На устройстве нет виджетов",
                        color = s.textSecondary,
                        fontSize = 15.sp,
                        fontFamily = s.fontFamily
                    )
                }
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(widgets) { w ->
                    val label = remember(w) {
                        runCatching { w.loadLabel(context.packageManager) }.getOrDefault("Виджет")
                    }
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(s.cardBg)
                            .clickable { onPick(w) }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Widgets,
                            contentDescription = null,
                            tint = s.accent,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            label,
                            color = s.textPrimary,
                            fontSize = 13.sp,
                            fontFamily = s.fontFamily,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
