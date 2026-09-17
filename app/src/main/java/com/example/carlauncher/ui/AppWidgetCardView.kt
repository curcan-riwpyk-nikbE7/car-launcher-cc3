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

/**
 * Отображение системного виджета Android (Яндекс Музыка и др.)
 * внутри правой карточки лаунчера.
 */
@Composable
fun AppWidgetCardView(
    widgetId: Int,
    onPickWidget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val s = LocalThemeSpec.current
    val widgetInfo = remember(widgetId) {
        if (widgetId > 0) AppWidgetHostManager.getAppWidgetInfo(widgetId) else null
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

            // Кнопка смены виджета в правом верхнем углу
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onPickWidget),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Сменить виджет",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            // Плейсхолдер, если виджет ещё не выбран
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onPickWidget)
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
