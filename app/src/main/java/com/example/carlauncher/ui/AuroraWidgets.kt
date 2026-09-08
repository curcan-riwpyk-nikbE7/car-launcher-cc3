package com.example.carlauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.AppInfo
import com.example.carlauncher.data.Weather
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Крупные часы справа + дата и компас — как в теме Aurora.
 * Часы «висят» прямо на фоне без карточки, компас в рамке.
 */
@Composable
fun HeroClockPanel(date: Date, modifier: Modifier = Modifier) {
    val s = LocalThemeSpec.current
    val timeFmt = remember0("HH:mm")
    val dateFmt = remember0("dd/MM/yyyy")
    val dayFmt = remember0("EEEE")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = timeFmt.format(date),
            color = s.textPrimary,
            fontSize = 54.sp,
            fontWeight = FontWeight.Light,
            fontFamily = s.fontFamily
        )

        // Дата с вертикальной чертой слева
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 2.dp, height = 34.dp)
                    .background(s.textSecondary.copy(alpha = 0.5f))
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = dateFmt.format(date),
                    color = s.textPrimary,
                    fontSize = 15.sp,
                    fontFamily = s.fontFamily
                )
                Text(
                    text = dayFmt.format(date).replaceFirstChar { it.uppercase() },
                    color = s.textSecondary,
                    fontSize = 13.sp,
                    fontFamily = s.fontFamily
                )
            }
        }
    }
}

/**
 * Строка погоды под часами Aurora: иконка состояния, температура
 * и короткое описание. Выровнена вправо — продолжает линию часов.
 */
@Composable
fun AuroraWeatherLine(weather: Weather, modifier: Modifier = Modifier) {
    val s = LocalThemeSpec.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = weatherIcon(weather.code, weather.isDay),
            contentDescription = weather.description,
            tint = weatherTint(weather.code),
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = "${weather.tempC}°",
            color = s.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = s.fontFamily
        )
        if (weather.description.isNotBlank()) {
            Text(
                text = weather.description.replaceFirstChar { it.lowercase() },
                color = s.textSecondary,
                fontSize = 13.sp,
                fontFamily = s.fontFamily
            )
        }
    }
}

/** Карточка компаса с направлением. */
@Composable
fun CompassCard(direction: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalThemeSpec.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .border(s.strokeWidth, s.cardStroke, RoundedCornerShape(s.cardCorner))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.Navigation,
            contentDescription = "Компас",
            tint = s.textPrimary,
            modifier = Modifier.size(26.dp)
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .size(width = 1.dp, height = 30.dp)
                .background(s.textSecondary.copy(alpha = 0.35f))
        )
        Text(
            text = direction,
            color = s.textPrimary,
            fontSize = 14.sp,
            fontFamily = s.fontFamily,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}

/**
 * Карточка FM-радио со стрелками перелистывания.
 *
 * Частоту радио ГУ не сообщает, поэтому по умолчанию в центре
 * показываем название станции/источника, а не выдуманную «87.50».
 * Число появляется, только если частота реально известна.
 */
@Composable
fun FmRadioCard(
    stationName: String = "",
    frequency: String? = null,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(s.radioGradient)
            )
            .border(s.strokeWidth, s.cardStroke, RoundedCornerShape(s.cardCorner))
    ) {
        // Декоративные дуги в левом верхнем углу
        if (s.showDecorRings) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                listOf(0.30f, 0.45f, 0.60f).forEach { f ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = size.minDimension * f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.12f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onOpen)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("FM", color = s.textPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, fontFamily = s.fontFamily)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.ChevronLeft, "Предыдущая",
                    tint = s.textPrimary,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPrev)
                        .padding(6.dp)
                )
                val center = when {
                    stationName.isNotBlank() -> stationName
                    frequency != null -> frequency
                    else -> "—"
                }
                Text(
                    text = center,
                    color = s.textPrimary,
                    fontSize = if (stationName.isNotBlank()) 17.sp else 34.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = s.fontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Icon(
                    Icons.Rounded.ChevronRight, "Следующая",
                    tint = s.textPrimary,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onNext)
                        .padding(6.dp)
                )
            }

            if (frequency != null) {
                Text("MHz", color = s.textPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, fontFamily = s.fontFamily)
            } else {
                // Без частоты нижняя подпись пустая — иначе «FM/MHz»
                // сверху и снизу дублировали друг друга.
                Text(" ", color = s.textPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, fontFamily = s.fontFamily)
            }
        }
    }
}

/**
 * Сетка приложений на фоне — иконка с подписью, без карточки.
 * Долгое нажатие назначает другое приложение.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGridRow(
    apps: List<AppInfo?>,
    onLaunch: (AppInfo) -> Unit,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.forEachIndexed { index, app ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(s.iconCorner))
                    .combinedClickable(
                        onClick = { app?.let(onLaunch) ?: onPick(index) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPick(index)
                        }
                    )
                    .padding(vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (app != null) {
                        AppIcon(app.icon, app.label, Modifier.size(44.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(s.iconCorner))
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Add, "Добавить",
                                tint = s.textSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Text(
                    text = app?.label ?: "Add",
                    color = if (app != null) s.textPrimary else s.textDim,
                    fontSize = 11.sp,
                    fontFamily = s.fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** Нижний док с системными кнопками. Центральная кнопка выделена кольцом. */
@Composable
fun BottomDock(
    items: List<Pair<ImageVector, () -> Unit>>,
    highlightIndex: Int,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (icon, action) ->
            if (i == highlightIndex) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, s.textPrimary, CircleShape)
                        .clickable(onClick = action)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = s.textPrimary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = action)
                        .padding(7.dp)
                )
            }
        }
    }
}

@Composable
private fun remember0(pattern: String): SimpleDateFormat =
    androidx.compose.runtime.remember(pattern) {
        SimpleDateFormat(pattern, Locale.getDefault())
    }
