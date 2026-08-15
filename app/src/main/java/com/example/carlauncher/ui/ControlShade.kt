package com.example.carlauncher.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TabletMac
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.SettingsActivity
import com.example.carlauncher.data.QuickControls
import com.example.carlauncher.data.SettingsStore
import com.example.carlauncher.data.ThemeStore
import com.example.carlauncher.data.rememberWeather
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Шторка быстрых настроек — выезжает сверху.
 *
 * В ней намеренно только то, что реально работает без системных прав.
 * Тумблера Wi-Fi нет: с Android 10 setWifiEnabled() запрещён, вместо
 * него кнопка открывает системную панель интернета. Перезагрузки ГУ
 * и мобильных данных нет по той же причине — они требуют подписи
 * прошивкой.
 *
 * @param onScreenOff гашение экрана держит MainActivity: яркость окна
 *        меняется на уровне Window, до которого из Compose не дотянуться.
 */
@Composable
fun ControlShade(
    visible: Boolean,
    onDismiss: () -> Unit,
    onScreenOff: () -> Unit,
    weatherKey: Int = 0
) {
    val context = LocalContext.current

    // Тик раз в секунду, пока шторка открыта: время должно идти,
    // а состояние тумблеров — обновляться, если их поменяли извне.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(visible) {
        while (visible) {
            kotlinx.coroutines.delay(1000)
            tick++
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(160))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Клик мимо шторки закрывает её. Без indication, иначе
                // по всему экрану расходится волна нажатия.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .background(Color.Black.copy(alpha = 0.55f))
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(220)) { -it },
                exit = slideOutVertically(tween(180)) { -it }
            ) {
                ShadePanel(
                    context = context,
                    tick = tick,
                    weatherKey = weatherKey,
                    onDismiss = onDismiss,
                    onScreenOff = onScreenOff
                )
            }
        }
    }
}

@Composable
private fun ShadePanel(
    context: Context,
    tick: Int,
    weatherKey: Int,
    onDismiss: () -> Unit,
    onScreenOff: () -> Unit
) {
    val s = LocalThemeSpec.current

    val now = remember(tick) { Date() }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        s.panelBg.copy(alpha = 0.99f),
                        s.bg.first().copy(alpha = 0.97f)
                    )
                )
            )
            // Клик по самой шторке не должен её закрывать
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
            // Свайп вверх по шторке закрывает — привычный жест
            .pointerInput(Unit) {
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dy = 0f },
                    onDragEnd = { if (dy < -60) onDismiss() }
                ) { change, drag ->
                    dy += drag.y
                    change.consume()
                }
            }
            .padding(horizontal = 30.dp)
            .padding(top = 18.dp, bottom = 14.dp)
    ) {
        // ─── шапка: время, дата, погода, шестерёнка ───
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    text = timeFmt.format(now),
                    color = s.textPrimary,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = s.fontFamily
                )
                Text(
                    text = dateFmt.format(now).replaceFirstChar { it.uppercase() },
                    color = s.textSecondary,
                    fontSize = 15.sp,
                    fontFamily = s.fontFamily
                )
            }

            val weather by rememberWeather(weatherKey)
            if (weather.valid) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 34.dp)
                ) {
                    Icon(
                        imageVector = weatherIcon(weather.code, weather.isDay),
                        contentDescription = weather.description,
                        tint = weatherTint(weather.code),
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text("${weather.tempC}°", color = s.textPrimary, fontSize = 22.sp, fontFamily = s.fontFamily)
                        Text(weather.description, color = s.textSecondary, fontSize = 12.sp, fontFamily = s.fontFamily)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Icon(
                Icons.Rounded.Settings,
                contentDescription = "Настройки лаунчера",
                tint = s.textSecondary,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable {
                        onDismiss()
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }
                    .padding(11.dp)
            )
        }

        // ─── шесть кнопок ───
        // Читаем состояние по tick: пользователь мог переключить BT
        // системной шторкой, пока наша открыта.
        val btOn = remember(tick) { QuickControls.isBluetoothOn(context) }
        val wifiOn = remember(tick) { QuickControls.isWifiOn(context) }
        val muted = remember(tick) { QuickControls.isMuted(context) }
        val themeTitle = remember(ThemeStore.current.value) {
            themeById(ThemeStore.current.value).title
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ShadeButton(Icons.Rounded.Wifi, "Интернет", wifiOn) {
                onDismiss()
                QuickControls.openInternetPanel(context)
            }

            ShadeButton(Icons.Rounded.Bluetooth, "Bluetooth", btOn) {
                // Если прямое переключение недоступно (Android 13+) —
                // уходим в настройки, чтобы нажатие не было впустую.
                if (!QuickControls.toggleBluetooth(context)) {
                    onDismiss()
                    QuickControls.openBluetoothSettings(context)
                }
            }

            ShadeButton(Icons.Rounded.Palette, "Тема", false, subtitle = themeTitle) {
                // Перебор по кругу: Violet → Blue → Gold → Black → Violet
                val ids = AllThemes.map { it.id }
                val next = ids[(ids.indexOf(ThemeStore.current.value) + 1).mod(ids.size)]
                ThemeStore.set(next)
            }

            ShadeButton(Icons.Rounded.DarkMode, "Ночь", SettingsStore.nightMode.value) {
                SettingsStore.setNightMode(!SettingsStore.nightMode.value)
            }

            ShadeButton(Icons.Rounded.TabletMac, "Экран выкл.", false) {
                onDismiss()
                onScreenOff()
            }

            ShadeButton(Icons.AutoMirrored.Rounded.VolumeOff, "Без звука", muted) {
                QuickControls.toggleMute(context)
            }
        }

        // ─── яркость ───
        BrightnessSlider(
            context = context,
            modifier = Modifier.padding(top = 24.dp)
        )

        // ─── ручка ───
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .align(Alignment.CenterHorizontally)
                .width(74.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(s.textDim)
        )
    }
}

/** Круглая кнопка шторки: включённая заливается акцентом. */
@Composable
private fun ShadeButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val s = LocalThemeSpec.current
    val haptic = LocalHapticFeedback.current

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(if (active) s.accent else Color.White.copy(alpha = 0.09f))
                .clickable {
                    if (SettingsStore.hapticEnabled.value) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) s.onAccent else s.textPrimary,
                modifier = Modifier.size(34.dp)
            )
        }
        Text(
            text = label,
            color = s.textPrimary,
            fontSize = 13.sp,
            fontFamily = s.fontFamily,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = s.textDim,
                fontSize = 11.sp,
                fontFamily = s.fontFamily
            )
        }
    }
}

/**
 * Ползунок яркости.
 *
 * Своя реализация вместо Slider: у материалового слайдера маленькая
 * зона захвата, в машине по ней трудно попасть. Здесь тянуть можно
 * за всю высоту полосы.
 */
@Composable
private fun BrightnessSlider(context: Context, modifier: Modifier = Modifier) {
    val s = LocalThemeSpec.current
    var value by remember { mutableFloatStateOf(QuickControls.brightness(context)) }
    var width by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .pointerInput(Unit) {
                width = size.width.toFloat()
                detectDragGestures(
                    onDragStart = { offset ->
                        // Разрешение спрашиваем в момент первого касания,
                        // а не при открытии шторки — иначе системный экран
                        // выскакивал бы у всех подряд без причины.
                        if (!QuickControls.canWriteSettings(context)) {
                            QuickControls.requestWriteSettings(context)
                        } else {
                            value = (offset.x / width).coerceIn(0f, 1f)
                            QuickControls.setBrightness(context, value)
                        }
                    }
                ) { change, drag ->
                    if (QuickControls.canWriteSettings(context)) {
                        value = (value + drag.x / width).coerceIn(0f, 1f)
                        QuickControls.setBrightness(context, value)
                    }
                    change.consume()
                }
            }
    ) {
        // Заливка. Минимум 58 dp, иначе при нуле от неё остаётся
        // некрасивый огрызок вместо круглой шапки.
        Box(
            modifier = Modifier
                .fillMaxWidth(value.coerceAtLeast(0.08f))
                .height(58.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(s.accent, s.accent2)))
        )

        Icon(
            Icons.Rounded.Brightness6,
            contentDescription = "Яркость",
            tint = s.onAccent,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp)
                .size(26.dp)
        )

        Text(
            text = "${(value * 100).toInt()}%",
            color = s.textPrimary,
            fontSize = 16.sp,
            fontFamily = s.fontFamily,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 22.dp)
        )
    }
}
