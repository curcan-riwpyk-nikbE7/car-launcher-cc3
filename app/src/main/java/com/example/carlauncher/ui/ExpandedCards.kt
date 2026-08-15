package com.example.carlauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.R
import com.example.carlauncher.data.TripComputer

/** Общая обёртка развёрнутой карточки: затемнение, свайп вниз, крестик. */
@Composable
private fun ExpandedShell(
    visible: Boolean,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    val s = LocalThemeSpec.current
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC050D18))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.74f)
                    .fillMaxHeight(0.78f)
                    .clip(RoundedCornerShape(s.cardCorner + 4.dp))
                    .background(s.cardBg)
                    .border(s.strokeWidth, s.cardStroke, RoundedCornerShape(s.cardCorner + 4.dp))
                    .clickable(enabled = false) { }
                    .pointerInput(Unit) {
                        var drag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = { if (drag > with(density) { 90.dp.toPx() }) onClose() }
                        ) { change, amount -> drag += amount; change.consume() }
                    }
            ) {
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
                content()
            }
        }
    }
}

/** Развёрнутое радио: крупная частота и большие кнопки. */
@Composable
fun ExpandedRadio(
    visible: Boolean,
    stationName: String,
    frequency: String,
    isPlaying: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    val s = LocalThemeSpec.current
    ExpandedShell(visible, onClose) {
        Column(
            modifier = Modifier.fillMaxSize().padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stationName, color = s.textSecondary, fontSize = 17.sp, fontFamily = s.fontFamily)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = frequency, color = s.textPrimary, fontSize = 72.sp,
                    fontWeight = FontWeight.Light, fontFamily = s.fontFamily
                )
                Text(
                    text = " MHz", color = s.accent, fontSize = 20.sp,
                    fontFamily = s.fontFamily, modifier = Modifier.padding(bottom = 14.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 26.dp)
            ) {
                BigRound(Icons.Rounded.SkipPrevious, "Назад", 66.dp, false, onPrev)
                BigRound(Icons.Rounded.Podcasts, "Открыть", 82.dp, true, onOpen)
                BigRound(Icons.Rounded.SkipNext, "Вперёд", 66.dp, false, onNext)
            }
            Text(
                text = if (isPlaying) stringResource(R.string.on_air) else stringResource(R.string.paused),
                color = if (isPlaying) s.accent else s.textDim,
                fontSize = 13.sp, fontFamily = s.fontFamily,
                modifier = Modifier.padding(top = 20.dp)
            )
        }
    }
}

/** Развёрнутая карточка авто: спидометр и трип-компьютер. */
@Composable
fun ExpandedCar(
    visible: Boolean,
    speedKmh: Int,
    onResetTrip: () -> Unit,
    onOpenCarInfo: () -> Unit = {},
    onClose: () -> Unit
) {
    val s = LocalThemeSpec.current
    ExpandedShell(visible, onClose) {
        Column(
            modifier = Modifier.fillMaxSize().padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = speedKmh.toString(), color = s.textPrimary,
                fontSize = 96.sp, fontWeight = FontWeight.Light, fontFamily = s.fontFamily
            )
            Text("km/h", color = s.textSecondary, fontSize = 16.sp, fontFamily = s.fontFamily)

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(top = 30.dp)
            ) {
                TripTile(Icons.Rounded.Route, stringResource(R.string.trip_distance), "${TripComputer.formattedDistance()} км")
                TripTile(Icons.Rounded.Speed, stringResource(R.string.trip_average), "${TripComputer.averageKmh} км/ч")
                TripTile(Icons.AutoMirrored.Rounded.TrendingUp, stringResource(R.string.trip_max), "${TripComputer.maxKmh.value} км/ч")
                TripTile(Icons.Rounded.Timer, stringResource(R.string.trip_time), TripComputer.formattedTime())
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 26.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(s.buttonCorner))
                        .clickable(onClick = onResetTrip)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, null, tint = s.accent, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.trip_reset), color = s.accent, fontSize = 14.sp,
                        fontFamily = s.fontFamily, modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Общий пробег и ТО живут на отдельном экране: здесь
                // показания текущей поездки, там история и обслуживание.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(s.buttonCorner))
                        .background(s.accent.copy(alpha = 0.14f))
                        .clickable(onClick = onOpenCarInfo)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Icon(Icons.Rounded.Build, null, tint = s.accent, modifier = Modifier.size(18.dp))
                    Text(
                        "Пробег и ТО", color = s.accent, fontSize = 14.sp,
                        fontFamily = s.fontFamily, modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TripTile(icon: ImageVector, title: String, value: String) {
    val s = LocalThemeSpec.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(s.iconCorner))
            .background(s.textPrimary.copy(alpha = 0.05f))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Icon(icon, title, tint = s.accent, modifier = Modifier.size(22.dp))
        Text(
            text = value, color = s.textPrimary, fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold, fontFamily = s.fontFamily,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = title, color = s.textSecondary, fontSize = 11.sp,
            fontFamily = s.fontFamily, modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun BigRound(
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

/** Ночное затемнение поверх интерфейса. */
@Composable
fun NightDim(active: Boolean) {
    AnimatedVisibility(
        visible = active,
        enter = fadeIn(tween(600)),
        exit = fadeOut(tween(600))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x59000814), Color(0x73000A18))
                    )
                )
        )
    }
}
