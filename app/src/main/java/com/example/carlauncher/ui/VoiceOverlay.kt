package com.example.carlauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.voice.VoiceEngine

/**
 * Плашка помощника поверх лаунчера.
 *
 * Показываем только когда есть что сказать: в режиме ожидания слова
 * активации не рисуем ничего, чтобы не отвлекать за рулём.
 */
@Composable
fun VoiceOverlay(
    state: VoiceEngine.State,
    partial: String,
    reply: String,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    val visible = state == VoiceEngine.State.Listening ||
        reply.isNotBlank() ||
        state == VoiceEngine.State.Loading

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 3 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(220)) { it / 3 },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(s.cardCorner))
                    .background(s.overlayBg)
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                PulsingMic(
                    active = state == VoiceEngine.State.Listening,
                    accent = s.accent
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = when {
                            state == VoiceEngine.State.Loading -> "Готовлю помощника…"
                            reply.isNotBlank() -> reply
                            partial.isNotBlank() -> partial
                            else -> "Слушаю"
                        },
                        color = s.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = s.fontFamily
                    )
                    if (state == VoiceEngine.State.Listening && reply.isBlank()) {
                        Text(
                            text = "скажите команду",
                            color = s.textDim,
                            fontSize = 12.sp,
                            fontFamily = s.fontFamily
                        )
                    }
                }
            }
        }
    }
}

/** Кружок микрофона: пульсирует, пока слушаем. */
@Composable
private fun PulsingMic(active: Boolean, accent: Color) {
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(760),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .scale(if (active) pulse else 1f)
            .clip(CircleShape)
            .background(accent.copy(alpha = if (active) 0.22f else 0.12f))
    ) {
        Icon(
            imageVector = if (active) Icons.Rounded.Mic else Icons.Rounded.MicOff,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Затемнение экрана вместо настоящего выключения подсветки.
 * На обычной сборке погасить экран нельзя, но чёрная вьюха поверх
 * решает исходную задачу — «чтобы не светило ночью».
 */
@Composable
fun ScreenDimOverlay(visible: Boolean, onWake: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(250))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = source,
                    indication = null,
                    onClick = onWake
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "касание — включить экран",
                color = Color(0x22FFFFFF),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
