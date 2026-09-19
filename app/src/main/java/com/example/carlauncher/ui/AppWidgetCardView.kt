package com.example.carlauncher.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.carlauncher.data.AppWidgetHostManager

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Settings
import com.example.carlauncher.data.AppRepository

/**
 * Отображение системного виджета Android (Яндекс Музыка и др.)
 * внутри центральной карточки лаунчера с кнопкой быстрого запуска
 * полноценного приложения и переключения режимов.
 */
@Composable
fun AppWidgetCardView(
    widgetId: Int,
    onPickWidget: () -> Unit,
    onChangeMode: () -> Unit = onPickWidget,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val s = LocalThemeSpec.current
    val widgetInfo = remember(widgetId) {
        if (widgetId > 0) AppWidgetHostManager.getAppWidgetInfo(widgetId) else null
    }
    val appLabel = remember(widgetInfo) {
        widgetInfo?.let { info ->
            runCatching {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(info.provider.packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrNull()
        } ?: "Приложение"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(s.cardCorner))
            .background(s.carCardBg)
    ) {
        if (widgetId > 0 && widgetInfo != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val frameLayout = FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    val view = AppWidgetHostManager.createView(ctx, widgetId)
                    if (view != null) {
                        view.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        frameLayout.addView(view)
                    }
                    frameLayout
                },
                update = { frameLayout ->
                    if (frameLayout.childCount == 0) {
                        val view = AppWidgetHostManager.createView(context, widgetId)
                        if (view != null) {
                            view.layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            frameLayout.addView(view)
                        }
                    }
                }
            )

            // Верхняя плашка действий над виджетом
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка прямого запуска самого приложения (YouTube, Яндекс.Музыка и др.)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .clickable {
                            AppRepository.launchPackage(context, widgetInfo.provider.packageName)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = "Открыть $appLabel",
                        tint = s.accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Открыть $appLabel",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = s.fontFamily
                    )
                }

                // Кнопки смены режима и виджета
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.70f))
                            .clickable(onClick = onChangeMode),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Режимы карточки",
                            tint = Color.White.copy(alpha = 0.90f),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.70f))
                            .clickable(onClick = onPickWidget),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Сменить виджет",
                            tint = Color.White.copy(alpha = 0.90f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        } else {
            // Плейсхолдер, если виджет ещё не выбран
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onChangeMode)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(s.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Widgets,
                        contentDescription = null,
                        tint = s.accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Выберите виджет",
                    color = s.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = s.fontFamily
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Нажмите здесь, чтобы выбрать Яндекс.Музыку, плеер или другой виджет",
                    color = s.textSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = s.fontFamily,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
