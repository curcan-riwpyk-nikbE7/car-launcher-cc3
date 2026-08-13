package com.example.carlauncher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.NowPlaying

/** Медиа-карточка с ярким градиентом, как на CC3. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCard(
    state: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenPlayer: () -> Unit,
    /** Удержание — развернуть плеер на весь экран. */
    onExpand: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CardCorner))
            .combinedClickable(
                onClick = { },
                onLongClick = {
                    haptic.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                    onExpand()
                }
            )
            // Базовый градиент берётся из активной темы
            .background(MediaGradient)
            // Неоновая кромка: на референсе карточка будто подсвечена изнутри
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        LocalThemeSpec.current.accent,
                        LocalThemeSpec.current.accent2,
                        LocalThemeSpec.current.accent
                    )
                ),
                shape = RoundedCornerShape(CardCorner)
            )
    ) {
        // Второй слой: пурпур затекает из правого нижнего угла.
        // На фото CC3 именно так: слева внизу голубой, справа внизу малиновый.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MediaCornerOverlay)
        )

        // Обложка альбома проступает сквозь градиент, если она есть
        state.artwork?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.35f)
            )
        }

        // Еле заметный «завиток» — только в темах, где он предусмотрен
        if (LocalThemeSpec.current.showDecorRings) Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.70f
            val cy = size.height * 0.30f
            listOf(0.16f, 0.28f, 0.40f).forEach { f ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = size.minDimension * f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.clickable(onClick = onOpenPlayer)) {
                    Text(
                        text = state.title,
                        color = Color.White,
                        fontSize = dimens().mediaTitle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.artist,
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Icon(
                    Icons.Rounded.DragIndicator, null,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(17.dp)
                )
            }

            EqualizerBars(
                playing = state.isPlaying,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CtrlIcon(Icons.Rounded.SkipPrevious, "Назад", 30.dp, onPrev)
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f))
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Плей/пауза",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                CtrlIcon(Icons.Rounded.SkipNext, "Вперёд", 30.dp, onNext)
            }
        }
    }
}

@Composable
private fun CtrlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Icon(
        icon, label,
        tint = Color.White,
        modifier = Modifier
            .size(size + 14.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(7.dp)
    )
}

/** Анимированный эквалайзер — двигается только когда играет музыка. */
@Composable
fun EqualizerBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color.White,
    bars: Int = 4
) {
    val transition = rememberInfiniteTransition(label = "eq")
    val phases = (0 until bars).map { i ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(520 + i * 140, easing = LinearEasing),
                RepeatMode.Reverse
            ),
            label = "bar$i"
        )
    }

    Row(
        modifier = modifier.size(width = 26.dp, height = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        phases.forEachIndexed { i, p ->
            val h = if (playing) p.value else 0.3f + i * 0.12f
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = (20 * h).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor.copy(alpha = 0.9f))
            )
        }
    }
}
