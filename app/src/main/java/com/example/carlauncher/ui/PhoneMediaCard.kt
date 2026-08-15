package com.example.carlauncher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.NowPlaying

/**
 * Медиа-виджет в виде смартфона с неоновым свечением вокруг корпуса.
 *
 * На макете это не плоская карточка, а именно силуэт телефона:
 * тёмный экран со скруглёнными углами, рамка корпуса и цветное
 * свечение по контуру. Свечение медленно переливается.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneMediaCard(
    state: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenPlayer: () -> Unit,
    onExpand: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    val haptic = LocalHapticFeedback.current

    // Свечение переливается по кругу
    val transition = rememberInfiniteTransition(label = "phoneGlow")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift"
    )

    val glowColors = listOf(
        s.accent2,
        s.accent,
        Color(0xFF5B8CFF),
        s.accent2
    )

    Box(
        modifier = modifier.combinedClickable(
            onClick = onOpenPlayer,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onExpand()
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        // Слой свечения: несколько размытых контуров вокруг корпуса
        Canvas(modifier = Modifier.fillMaxSize()) {
            val phoneW = size.width * 0.78f
            val phoneH = size.height * 0.94f
            val left = (size.width - phoneW) / 2f
            val top = (size.height - phoneH) / 2f
            val corner = phoneW * 0.13f

            // Рисуем расширяющиеся контуры с падающей прозрачностью —
            // получается мягкое неоновое гало без библиотеки размытия.
            for (i in 7 downTo 1) {
                val spread = i * 4.dp.toPx()
                val alpha = 0.16f / i
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = glowColors.map { it.copy(alpha = alpha) },
                        start = Offset(0f, size.height * shift),
                        end = Offset(size.width, size.height * (1f - shift))
                    ),
                    topLeft = Offset(left - spread, top - spread),
                    size = androidx.compose.ui.geometry.Size(
                        phoneW + spread * 2, phoneH + spread * 2
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        corner + spread, corner + spread
                    )
                )
            }
        }

        // Корпус телефона
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(percent = 13))
                .background(Color(0xFF0B0C15))
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = glowColors,
                        start = Offset(0f, 1000f * shift),
                        end = Offset(1000f, 1000f * (1f - shift))
                    ),
                    shape = RoundedCornerShape(percent = 13)
                )
        ) {
            // Обложка альбома проступает на экране телефона
            state.artwork?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xF20B0C15))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Верх: «динамик» и значок ноты
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(width = 26.dp, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.18f)))
                    Icon(
                        Icons.Rounded.MusicNote, null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(Modifier.weight(0.5f))

                // Строка Bluetooth
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Bluetooth, null,
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(14.dp)
                    )
                    // Имя телефона вместо версии протокола: «Bluetooth 5.1»
                    // не говорит ничего, а имя устройства сразу показывает,
                    // чей телефон сейчас подключён.
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val deviceName = remember {
                        com.example.carlauncher.data.BtDevice.connectedName(ctx)
                    }
                    Text(
                        text = deviceName ?: "Bluetooth",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontFamily = s.fontFamily,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }

                Box(Modifier.weight(0.5f))

                // Название и исполнитель
                Text(
                    text = state.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = s.fontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = state.artist,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = s.fontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )

                Box(Modifier.weight(0.6f))

                // Полоса прогресса с градиентной заливкой
                val fraction = if (state.durationMs > 0L) {
                    (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0.35f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Brush.horizontalGradient(listOf(s.accent, s.accent2)))
                    )
                }

                Box(Modifier.weight(0.5f))

                // Кнопки управления
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhoneCtrl(Icons.Rounded.SkipPrevious, "Назад", onPrev)
                    PhoneCtrl(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        "Плей/пауза", onPlayPause, big = true
                    )
                    PhoneCtrl(Icons.Rounded.SkipNext, "Вперёд", onNext)
                }
            }
        }
    }
}

@Composable
private fun PhoneCtrl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    big: Boolean = false
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = Color.White,
        modifier = Modifier
            .size(if (big) 40.dp else 34.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(if (big) 4.dp else 5.dp)
    )
}
