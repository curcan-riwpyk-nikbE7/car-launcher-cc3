package com.example.carlauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.SettingsStore
import kotlin.math.abs

/** Что показать в подсказке после жеста. */
enum class GestureAction { NextTrack, PrevTrack, Volume }

/**
 * Состояние всплывающей подсказки. Держит тип действия и уровень громкости,
 * само прячется через 900 мс после последнего жеста.
 */
class GestureFeedbackState {
    var action by mutableStateOf<GestureAction?>(null)
        private set
    var volume by mutableStateOf(0f)
        private set
    var token by mutableIntStateOf(0)
        private set

    fun show(a: GestureAction, level: Float = 0f) {
        action = a
        volume = level
        token++
    }

    fun hide() {
        action = null
    }
}

@Composable
fun rememberGestureFeedback(): GestureFeedbackState {
    val state = remember { GestureFeedbackState() }
    // Каждый новый жест перезапускает таймер скрытия.
    LaunchedEffect(state.token) {
        if (state.action != null) {
            kotlinx.coroutines.delay(900)
            state.hide()
        }
    }
    return state
}

/**
 * Жесты по всему экрану:
 *  - свайп влево  -> следующий трек
 *  - свайп вправо -> предыдущий трек
 *  - тянуть вверх -> громче, вниз -> тише (пошагово, пока тянешь)
 *
 * Ось определяется по первому заметному движению и дальше не меняется,
 * чтобы «косой» свайп не менял и трек, и громкость одновременно.
 * События потребляются только после того, как жест распознан, — поэтому
 * обычные нажатия на карточки продолжают работать как раньше.
 */
@Composable
fun Modifier.launcherGestures(
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onVolumeStep: (up: Boolean) -> Unit,
    enabled: Boolean = true
): Modifier {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // Пороги берутся из настроек лаунчера
    val swipeDp = SettingsStore.swipeThreshold.value
    val volumeDp = SettingsStore.volumeStep.value
    val hapticOn = SettingsStore.hapticEnabled.value
    val swipeThreshold = with(density) { swipeDp.dp.toPx() }
    val axisLock = with(density) { 18.dp.toPx() }
    val volumeStep = with(density) { volumeDp.dp.toPx() }

    return this.pointerInput(enabled, swipeDp, volumeDp) {
        if (!enabled) return@pointerInput

        var totalX = 0f
        var totalY = 0f
        var axis = 0            // 0 — не решено, 1 — горизонталь, 2 — вертикаль
        var volumeAccum = 0f
        var fired = false       // чтобы один свайп не сработал дважды

        detectDragGestures(
            onDragStart = {
                totalX = 0f; totalY = 0f; axis = 0; volumeAccum = 0f; fired = false
            },
            onDragCancel = { axis = 0 },
            onDragEnd = {
                if (axis == 1 && !fired) {
                    when {
                        totalX <= -swipeThreshold -> {
                            if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNextTrack()
                        }
                        totalX >= swipeThreshold -> {
                            if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPrevTrack()
                        }
                    }
                }
                axis = 0
            },
            onDrag = { change, amount ->
                totalX += amount.x
                totalY += amount.y

                // Определяем ось один раз: доминирующее направление с запасом 1.3x
                if (axis == 0) {
                    val ax = abs(totalX)
                    val ay = abs(totalY)
                    if (ax > axisLock && ax > ay * 1.3f) axis = 1
                    else if (ay > axisLock && ay > ax * 1.3f) axis = 2
                }

                if (axis == 2) {
                    volumeAccum += amount.y
                    // Вверх = громче: у экрана ось Y растёт вниз, поэтому знак минус.
                    while (volumeAccum <= -volumeStep) {
                        onVolumeStep(true); volumeAccum += volumeStep
                    }
                    while (volumeAccum >= volumeStep) {
                        onVolumeStep(false); volumeAccum -= volumeStep
                    }
                }

                // Забираем событие себе только когда жест уже распознан,
                // иначе сломаются обычные тапы по карточкам.
                if (axis != 0) change.consume()
            }
        )
    }
}

/** Всплывающая подсказка по центру экрана: трек или шкала громкости. */
@Composable
fun GestureOverlay(state: GestureFeedbackState, modifier: Modifier = Modifier) {
    val action = state.action

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = action != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xE60B1046))
                    .padding(horizontal = 26.dp, vertical = 20.dp)
            ) {
                when (action) {
                    GestureAction.Volume -> VolumeIndicator(state.volume)
                    GestureAction.NextTrack -> TrackIndicator(next = true)
                    GestureAction.PrevTrack -> TrackIndicator(next = false)
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun VolumeIndicator(level: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = when {
                level <= 0.001f -> Icons.AutoMirrored.Rounded.VolumeOff
                level < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                else -> Icons.AutoMirrored.Rounded.VolumeUp
            },
            contentDescription = "Громкость",
            tint = Cyan,
            modifier = Modifier.size(38.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .width(170.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(level.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Cyan)
            )
        }
        Text(
            text = "${(level * 100).toInt()}%",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun TrackIndicator(next: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (next) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious,
            contentDescription = null,
            tint = Cyan,
            modifier = Modifier.size(38.dp)
        )
        Text(
            text = if (next) "Следующий трек" else "Предыдущий трек",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
