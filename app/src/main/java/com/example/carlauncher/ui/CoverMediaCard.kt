package com.example.carlauncher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.NowPlaying

/**
 * Медиа-карточка в стиле штатного CC3.
 *
 * Ключевое отличие от прежней версии: обложка альбома — это и есть
 * вся карточка, а не маленький квадратик сбоку. Поверх неё ложатся
 * название с исполнителем (сверху слева) и кнопки управления (снизу).
 * Чтобы белый текст читался на любой обложке, сверху и снизу
 * подмешиваем тёмный градиент.
 *
 * Пока обложки нет — показываем градиент темы, чтобы карточка не
 * выглядела пустой дырой.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoverMediaCard(
    state: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenPlayer: () -> Unit,
    /** Удержание — развернуть плеер на весь экран. */
    onExpand: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(Brush.linearGradient(s.mediaGradient))
            .combinedClickable(
                onClick = onOpenPlayer,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onExpand()
                }
            )
    ) {
        // --- обложка на всю карточку ---
        // Crossfade, чтобы при смене трека картинка не «прыгала».
        Crossfade(targetState = state.artwork, label = "cover") { art ->
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Затемнение сверху и снизу — под текст и кнопки.
        // Середина остаётся чистой, обложку видно.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.35f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f)
                    )
                )
        )

        // --- название и исполнитель: сверху слева ---
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 14.dp, end = 56.dp)
        ) {
            Text(
                text = state.title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = s.fontFamily
            )
            Text(
                text = state.artist,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = s.fontFamily
            )
        }

        // Облачко-источник в правом верхнем углу — как у CC3
        Icon(
            imageVector = Icons.Rounded.CloudQueue,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(22.dp)
        )

        // --- полоса прогресса над кнопками ---
        // Плеер сообщает позицию не каждую секунду, а рывками, поэтому
        // между обновлениями докручиваем её локально по часам — иначе
        // полоса стояла бы на месте и дёргалась раз в несколько секунд.
        if (state.durationMs > 0L) {
            var tick by remember { mutableStateOf(0L) }
            LaunchedEffect(state.isPlaying, state.positionAt) {
                while (state.isPlaying) {
                    tick = android.os.SystemClock.elapsedRealtime()
                    kotlinx.coroutines.delay(500)
                }
            }
            val live = remember(tick, state.positionMs, state.positionAt, state.isPlaying) {
                if (state.isPlaying) {
                    val elapsed = android.os.SystemClock.elapsedRealtime() - state.positionAt
                    (state.positionMs + elapsed).coerceIn(0L, state.durationMs)
                } else state.positionMs.coerceIn(0L, state.durationMs)
            }
            val frac = (live.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 74.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = fmtTime(live),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontFamily = s.fontFamily
                    )
                    Box(Modifier.weight(1f))
                    Text(
                        text = fmtTime(state.durationMs),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontFamily = s.fontFamily
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.28f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(s.accent)
                    )
                }
            }
        }

        // --- кнопки снизу по центру ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverButton(Icons.Rounded.SkipPrevious, 30.dp, onPrev)
            Box(Modifier.padding(horizontal = 22.dp)) {
                CoverButton(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    38.dp,
                    onPlayPause,
                    filled = true
                )
            }
            CoverButton(Icons.Rounded.SkipNext, 30.dp, onNext)
        }
    }
}

/** Кнопка управления поверх обложки: белая, с полупрозрачной подложкой. */
@Composable
private fun CoverButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    filled: Boolean = false
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(if (filled) size + 18.dp else size + 12.dp)
            .clip(CircleShape)
            .background(
                if (filled) Color.White.copy(alpha = 0.22f)
                else Color.Transparent
            )
            .combinedClickableSimple(onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size)
        )
    }
}

/** Обычный клик без ряби — на обложке волна смотрится грязно. */
@Composable
private fun Modifier.combinedClickableSimple(onClick: () -> Unit): Modifier {
    val source = androidx.compose.runtime.remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    return this.then(
        clickable(
            interactionSource = source,
            indication = null,
            onClick = onClick
        )
    )
}

/** Миллисекунды в «м:сс» — длиннее часа треков в машине не бывает. */
private fun fmtTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
