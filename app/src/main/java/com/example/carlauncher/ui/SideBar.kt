package com.example.carlauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Панель управления. Одна и та же для всех тем, но ориентация
 * (вертикальная колонка или горизонтальный док) выбирается по раскладке темы.
 */
@Composable
fun LauncherPanel(
    modifier: Modifier,
    time: String,
    date: Date,
    onAssistant: () -> Unit,
    onSettings: () -> Unit,
    onSystemSettings: () -> Unit,
    onAllApps: () -> Unit,
    onNavigation: () -> Unit
) {
    val s = LocalThemeSpec.current
    val d = dimens()
    val horizontal = s.layout == LayoutStyle.BottomDock || s.layout == LayoutStyle.TopBar ||
        s.layout == LayoutStyle.GridDock

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(s.panelBg)
            .border(s.strokeWidth, s.cardStroke, RoundedCornerShape(s.cardCorner))
            .padding(
                start = if (horizontal) 18.dp else 0.dp,
                end = if (horizontal) 18.dp else 0.dp,
                top = if (horizontal) 8.dp else 18.dp,
                bottom = 0.dp
            )
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                PanelClock(time, date)
                Box(Modifier.weight(1f))
                AssistantOrb(onAssistant)
                PanelIcon(LauncherIcons.Sliders, "Настройки лаунчера", onSettings)
                PanelIcon(LauncherIcons.Gear, "Настройки Android", onSystemSettings)
                PanelIcon(LauncherIcons.Cube, "Все приложения", onAllApps)
                NavButton(onNavigation)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PanelClock(time, date)
                // Порядок и шаг повторяют штатный лаунчер CC3:
                // часы → орб → шестерёнка → куб → (пустота) → кнопка навигации.
                // Иконок ровно две: третья ломала равномерный шаг 129 px.
                // Долгое нажатие на шестерёнку открывает настройки Android —
                // так пункт не потерялся, но панель осталась чистой.
                Box(Modifier.padding(top = d.panelGap)) { AssistantOrb(onAssistant) }
                PanelIcon(LauncherIcons.Gear, "Настройки", onSettings,
                    Modifier.padding(top = d.panelGap * 1.25f),
                    onLongClick = onSystemSettings)
                PanelIcon(LauncherIcons.Cube, "Все приложения", onAllApps,
                    Modifier.padding(top = d.panelGap * 1.25f))
                Box(Modifier.weight(1f))
                NavButton(onNavigation, Modifier.fillMaxWidth().height(d.navButtonHeight))
            }
        }
    }
}

/** Часы: крупные, компактные или аналоговые — по стилю темы. */
@Composable
private fun PanelClock(time: String, date: Date) {
    val s = LocalThemeSpec.current
    when (s.clockStyle) {
        // В Aurora часы рисуются отдельным блоком справа, в панели их нет.
        ClockStyle.HeroRight -> Unit
        ClockStyle.Analog -> AnalogClock(date)
        ClockStyle.DigitalCompact -> Text(
            text = time,
            color = s.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = s.fontFamily
        )
        ClockStyle.DigitalLarge -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                color = s.textPrimary,
                fontSize = dimens().clockSize,
                fontWeight = FontWeight.Medium,
                fontFamily = s.fontFamily
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (i < 3) s.textPrimary else s.textDim)
                    )
                }
            }
            Text(
                text = "4G",
                color = s.textSecondary,
                fontSize = 11.sp,
                fontFamily = s.fontFamily,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** Аналоговые часы со стрелками — для ретро-темы. */
@Composable
private fun AnalogClock(date: Date) {
    val s = LocalThemeSpec.current
    val hFmt = remember { SimpleDateFormat("h", Locale.getDefault()) }
    val mFmt = remember { SimpleDateFormat("m", Locale.getDefault()) }
    val h = (hFmt.format(date).toIntOrNull() ?: 0) % 12
    val m = mFmt.format(date).toIntOrNull() ?: 0

    Canvas(modifier = Modifier.size(46.dp)) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(color = s.accent, radius = r - 2.dp.toPx(), style = Stroke(width = 2.dp.toPx()))

        val hAngle = Math.toRadians(((h + m / 60f) * 30f - 90f).toDouble())
        val mAngle = Math.toRadians((m * 6f - 90f).toDouble())
        drawLine(
            color = s.textPrimary,
            start = c,
            end = Offset(c.x + (r * 0.48f) * cos(hAngle).toFloat(), c.y + (r * 0.48f) * sin(hAngle).toFloat()),
            strokeWidth = 3.dp.toPx()
        )
        drawLine(
            color = s.accent,
            start = c,
            end = Offset(c.x + (r * 0.72f) * cos(mAngle).toFloat(), c.y + (r * 0.72f) * sin(mAngle).toFloat()),
            strokeWidth = 2.dp.toPx()
        )
        drawCircle(color = s.accent, radius = 2.5.dp.toPx(), center = c)
    }
}

/** Пульсирующий круг ассистента. Форма зависит от темы. */
@Composable
private fun AssistantOrb(onClick: () -> Unit) {
    val s = LocalThemeSpec.current
    val transition = rememberInfiniteTransition(label = "orb")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    val d = dimens()
    val shape = if (s.cardCorner < 8.dp) RoundedCornerShape(4.dp) else CircleShape

    Box(
        modifier = Modifier
            .size(d.orbSize)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Мягкое свечение вокруг кольца
        Canvas(modifier = Modifier.size(d.orbSize)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        s.accent.copy(alpha = 0.30f),
                        s.accent2.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.minDimension / 2
                ),
                radius = size.minDimension / 2
            )
        }
        // Само кольцо: толстое, с вращающимся градиентом
        Canvas(modifier = Modifier.size(d.orbRing).rotate(angle)) {
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(s.accent, s.accent2, s.accent),
                    center = Offset(size.width / 2, size.height / 2)
                ),
                radius = size.minDimension / 2 - 4.dp.toPx(),
                style = Stroke(width = 7.dp.toPx())
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PanelIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Долгое нажатие. Нужно, чтобы спрятать редкий пункт и не плодить иконки. */
    onLongClick: (() -> Unit)? = null
) {
    val s = LocalThemeSpec.current
    val haptic = LocalHapticFeedback.current
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = s.textSecondary,
        modifier = modifier
            .size(dimens().panelIcon)
            .clip(if (s.cardCorner < 8.dp) RoundedCornerShape(4.dp) else CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let {
                    {
                        // Отклик пальцу: за рулём подтверждение нажатия важно
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                }
            )
            .padding(dimens().panelIcon * 0.23f)
    )
}

/**
 * Кнопка навигации внизу панели.
 *
 * В вертикальной раскладке — крупный блок с диагональным срезом сверху,
 * как на эталонной панели. В горизонтальном доке остаётся компактной.
 */
@Composable
private fun NavButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalThemeSpec.current
    val wide = modifier != Modifier

    val shape = when {
        !wide -> RoundedCornerShape(s.buttonCorner)
        s.cardCorner < 8.dp -> NavCornerShape(diagonal = 0.42f, corner = 0f)
        else -> NavCornerShape(diagonal = 0.42f, corner = with(LocalDensity.current) { s.cardCorner.toPx() })
    }

    Box(
        modifier = (if (wide) modifier else Modifier.size(width = 58.dp, height = 50.dp))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(s.accent2, s.accent),
                    start = Offset(0f, Float.POSITIVE_INFINITY),
                    end = Offset(Float.POSITIVE_INFINITY, 0f)
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = if (wide) Alignment.BottomCenter else Alignment.Center
    ) {
        Icon(
            LauncherIcons.NavArrow,
            contentDescription = "Навигация",
            tint = Color.White,
            modifier = Modifier
                .padding(bottom = if (wide) 26.dp else 0.dp)
                .size(if (wide) 40.dp else 26.dp)
        )
    }
}
