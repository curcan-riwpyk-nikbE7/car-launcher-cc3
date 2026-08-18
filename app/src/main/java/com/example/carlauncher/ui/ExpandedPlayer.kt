package com.example.carlauncher.ui

import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.R
import com.example.carlauncher.data.NowPlaying

/**
 * Развёрнутый плеер поверх всего экрана.
 *
 * Открывается удержанием на медиа-карточке. Здесь крупная обложка,
 * большие кнопки и эквалайзер — всё, что мелко в обычной карточке.
 */
@Composable
fun ExpandedPlayer(
    visible: Boolean,
    state: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenApp: () -> Unit,
    onSeek: (Long) -> Unit = {},
    canSeek: Boolean = false,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Клик по затемнению закрывает — привычное поведение
                .background(Color(0xCC050D18))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .fillMaxHeight(0.82f)
                    .clip(RoundedCornerShape(s.cardCorner + 4.dp))
                    .background(s.cardBg)
                    .border(s.strokeWidth, s.cardStroke, RoundedCornerShape(s.cardCorner + 4.dp))
                    // Перехватываем клик, чтобы он не закрывал окно
                    .clickable(enabled = false) { }
                    // Свайп вниз закрывает — привычнее, чем целиться в крестик
                    .pointerInput(Unit) {
                        var drag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag > with(density) { 90.dp.toPx() }) onClose()
                            }
                        ) { change, amount ->
                            drag += amount
                            change.consume()
                        }
                    }
            ) {
                // Обложка размыто на фоне — даёт цвет альбома всему окну
                state.artwork?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(38.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        s.cardBg.copy(alpha = 0.72f),
                                        s.cardBg.copy(alpha = 0.94f)
                                    )
                                )
                            )
                    )
                }

                Icon(
                    Icons.Rounded.Close, stringResource(R.string.close),
                    tint = s.textSecondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(s.textPrimary.copy(alpha = 0.08f))
                        .clickable(onClick = onClose)
                        .padding(9.dp)
                )

                Row(
                    modifier = Modifier.fillMaxSize().padding(26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Крупная обложка
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.9f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(s.cardCorner))
                            .background(
                                Brush.linearGradient(s.mediaGradient)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val art = state.artwork
                        if (art != null) {
                            Image(
                                bitmap = art.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Rounded.MusicNote, null,
                                tint = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 26.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.title,
                            color = s.textPrimary,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = s.fontFamily,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.artist,
                            color = s.textSecondary,
                            fontSize = 17.sp,
                            fontFamily = s.fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp)
                        )

                        BigEqualizer(state.isPlaying, Modifier.padding(top = 18.dp))

                        if (state.durationMs > 0L) {
                            SeekBar(
                                state = state,
                                enabled = canSeek,
                                onSeek = onSeek,
                                modifier = Modifier.padding(top = 18.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.padding(top = 26.dp)
                        ) {
                            BigCtrl(Icons.Rounded.SkipPrevious, "Назад", 60.dp, false, onPrev)
                            BigCtrl(
                                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                "Плей/пауза", 76.dp, true, onPlayPause
                            )
                            BigCtrl(Icons.Rounded.SkipNext, "Вперёд", 60.dp, false, onNext)
                        }

                        // Ряд под кнопками: источник звука и переход
                        // в штатное приложение.
                        //
                        // Для Bluetooth это не украшение. Пока приложение
                        // BT-музыки не поднято, аудиоканал закрыт и телефон
                        // играет «в никуда» — раньше приходилось искать его
                        // руками в меню. Теперь кнопка рядом с плеером,
                        // и подпись говорит, чей телефон подключён.
                        val ctxPlayer = androidx.compose.ui.platform.LocalContext.current
                        val btName = remember(state.isBluetooth) {
                            if (state.isBluetooth) {
                                com.example.carlauncher.data.BtDevice.connectedName(ctxPlayer)
                            } else null
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 22.dp)
                        ) {
                            if (state.isBluetooth) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(s.buttonCorner))
                                        .background(Color.White.copy(alpha = 0.07f))
                                        .clickable {
                                            com.example.carlauncher.data.BtMusicStarter
                                                .openBtMusicApp(ctxPlayer)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Bluetooth, null,
                                        tint = Color(0xFF4FC3F7),
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Text(
                                        text = btName ?: "BT-музыка",
                                        color = s.textPrimary,
                                        fontSize = 14.sp,
                                        fontFamily = s.fontFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(s.buttonCorner))
                                    .clickable(onClick = onOpenApp)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.OpenInFull, null,
                                    tint = s.accent, modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = stringResource(R.string.open_player),
                                    color = s.accent,
                                    fontSize = 14.sp,
                                    fontFamily = s.fontFamily,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigCtrl(
    icon: ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    filled: Boolean,
    onClick: () -> Unit
) {
    val s = LocalThemeSpec.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) s.accent else s.textPrimary.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, label,
            tint = if (filled) s.onAccent else s.textPrimary,
            modifier = Modifier.size(size * 0.45f)
        )
    }
}

/** Крупный эквалайзер — виден с водительского места. */
@Composable
private fun BigEqualizer(playing: Boolean, modifier: Modifier = Modifier) {
    val s = LocalThemeSpec.current
    val transition = rememberInfiniteTransition(label = "bigEq")
    Row(
        modifier = modifier.height(34.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(9) { i ->
            val v by transition.animateFloat(
                initialValue = 0.22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(480 + i * 85, easing = LinearEasing), RepeatMode.Reverse
                ),
                label = "b$i"
            )
            val h = if (playing) v else 0.3f
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = (34 * h).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i % 2 == 0) s.accent else s.accent2)
            )
        }
    }
}

/**
 * Полоса прогресса с перемоткой.
 *
 * Позиция от плеера приходит редко, поэтому между обновлениями она
 * докручивается локально по системным часам — иначе полоса стояла бы
 * на месте и дёргалась рывками раз в несколько секунд.
 */
@Composable
private fun SeekBar(
    state: NowPlaying,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var tick by remember { mutableStateOf(0L) }

    // Тикаем раз в секунду, пока играет музыка
    LaunchedEffect(state.isPlaying, state.positionAt) {
        while (state.isPlaying) {
            tick = android.os.SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(1000)
        }
    }

    val livePosition = remember(state, tick) {
        if (state.isPlaying) {
            val elapsed = android.os.SystemClock.elapsedRealtime() - state.positionAt
            (state.positionMs + elapsed).coerceIn(0L, state.durationMs)
        } else state.positionMs.coerceIn(0L, state.durationMs)
    }

    val fraction = dragFraction
        ?: (livePosition.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth(0.92f)) {
        Slider(
            value = fraction,
            onValueChange = { if (enabled) dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * state.durationMs).toLong()) }
                dragFraction = null
            },
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = s.accent,
                activeTrackColor = s.accent,
                inactiveTrackColor = s.textPrimary.copy(alpha = 0.16f),
                disabledThumbColor = s.textDim,
                disabledActiveTrackColor = s.textDim
            ),
            modifier = Modifier.height(26.dp)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTime((fraction * state.durationMs).toLong()),
                color = s.textSecondary, fontSize = 12.sp, fontFamily = s.fontFamily
            )
            Box(Modifier.weight(1f))
            Text(
                text = formatTime(state.durationMs),
                color = s.textSecondary, fontSize = 12.sp, fontFamily = s.fontFamily
            )
        }
        if (!enabled) {
            Text(
                text = stringResource(R.string.no_seek_support),
                color = s.textDim, fontSize = 10.sp, fontFamily = s.fontFamily
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
