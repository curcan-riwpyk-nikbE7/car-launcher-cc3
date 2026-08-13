package com.example.carlauncher.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Карточка радио.
 *
 * Показывает станцию, частоту и живой эквалайзер в градиентном блоке.
 * Кнопки перемотки крупные — по ним удобно попадать на ходу.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadioCard(
    stationName: String,
    frequency: String = "87.50",
    isPlaying: Boolean = true,
    onOpen: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(s.cardBg)
            .border(s.strokeWidth, s.cardStroke, RoundedCornerShape(s.cardCorner))
            .combinedClickable(
                onClick = { },
                onLongClick = {
                    haptic.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                    onExpand()
                }
            )
            .padding(dimens().screenPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = themedLabel("Радио"),
                        color = s.textSecondary,
                        fontSize = 16.sp,
                        fontFamily = s.fontFamily
                    )
                    // Индикатор эфира
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) s.accent else s.textDim)
                    )
                }

                Column {
                    Text(
                        text = stationName,
                        color = s.textPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = s.fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onOpen)
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = frequency,
                            color = s.accent,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = s.fontFamily
                        )
                        Text(
                            text = " FM",
                            color = s.textSecondary,
                            fontSize = 15.sp,
                            fontFamily = s.fontFamily,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                // Шкала частот — как у штатного радио CC3.
                // Одна цифра без шкалы не даёт понять, где мы в диапазоне.
                FreqScale(frequency, s.accent, s.accent2, s.textDim)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CtrlButton(Icons.Rounded.SkipPrevious, "Предыдущая", onPrev)
                    CtrlButton(Icons.Rounded.SkipNext, "Следующая", onNext)
                    CtrlButton(Icons.Rounded.Podcasts, "Открыть радио", onOpen)
                }
            }

            // Градиентный блок с живой волной
            Box(
                modifier = Modifier
                    .width(dimens().radioWave)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(s.iconCorner))
                    .background(Brush.verticalGradient(s.radioGradient))
                    .clickable(onClick = onOpen),
                contentAlignment = Alignment.Center
            ) {
                WaveBars(isPlaying)
            }
        }
    }
}

@Composable
private fun CtrlButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val s = LocalThemeSpec.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(if (s.cardCorner < 8.dp) RoundedCornerShape(3.dp) else CircleShape)
            .background(s.textPrimary.copy(alpha = 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = s.textPrimary, modifier = Modifier.size(19.dp))
    }
}

/**
 * Звуковая волна. В эфире полоски дышат, в паузе замирают —
 * видно состояние, не читая текст.
 */
@Composable
private fun WaveBars(playing: Boolean) {
    val s = LocalThemeSpec.current
    val heights = listOf(0.32f, 0.58f, 0.85f, 1f, 0.72f, 0.45f, 0.66f, 0.30f)
    val transition = rememberInfiniteTransition(label = "wave")

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    ) {
        heights.forEachIndexed { i, base ->
            val anim by transition.animateFloat(
                initialValue = base,
                targetValue = (base * 0.45f).coerceAtLeast(0.18f),
                animationSpec = infiniteRepeatable(
                    tween(560 + i * 90, easing = LinearEasing),
                    RepeatMode.Reverse
                ),
                label = "bar$i"
            )
            val h = if (playing) anim else base * 0.6f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .size(width = 5.dp, height = (58 * h).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (i % 2 == 0) Color.White.copy(alpha = 0.95f)
                        else s.accent.copy(alpha = 0.9f)
                    )
            )
        }
    }
}

/**
 * Линейка диапазона FM 87.5…108 МГц с меткой текущей станции.
 *
 * Рисуем Canvas, а не набором вьюх: полсотни рисок отдельными
 * элементами — лишняя нагрузка на слабом процессоре ГУ.
 */
@Composable
private fun FreqScale(
    frequency: String,
    accent: Color,
    accent2: Color,
    dim: Color
) {
    val value = remember(frequency) {
        frequency.replace(',', '.').filter { it.isDigit() || it == '.' }
            .toFloatOrNull()?.coerceIn(87.5f, 108f) ?: 87.5f
    }
    val pos = (value - 87.5f) / (108f - 87.5f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .padding(end = 8.dp)
    ) {
        val n = 42
        val step = size.width / (n - 1)
        for (i in 0 until n) {
            val x = i * step
            val tall = if (i % 5 == 0) size.height * 0.55f else size.height * 0.28f
            drawLine(
                color = dim,
                start = Offset(x, size.height * 0.72f),
                end = Offset(x, size.height * 0.72f - tall),
                strokeWidth = 2f
            )
        }
        val mx = size.width * pos
        drawLine(
            color = accent2,
            start = Offset(mx, size.height * 0.82f),
            end = Offset(mx, size.height * 0.06f),
            strokeWidth = 3.5f
        )
    }
}
