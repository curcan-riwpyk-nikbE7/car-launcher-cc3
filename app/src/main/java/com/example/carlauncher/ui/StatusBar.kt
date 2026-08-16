package com.example.carlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import com.example.carlauncher.data.QuickControls
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.rememberWeather
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Полоса статуса во всю ширину экрана.
 *
 * Лежит поверх всего и своей высоты не занимает — карточки от этого
 * не становятся ниже. Градиент нужен потому, что под полосой оказывается
 * картинка машины: на светлых её местах белые значки без затемнения
 * пропадают. Сверху вниз он сходит на нет, чтобы не было видно границы.
 *
 * Часы сюда не выносим: у штатного CC3 они в боковой панели.
 */
@Composable
fun TopStatusStrip(
    modifier: Modifier = Modifier,
    weatherKey: Int = 0,
    onOpenShade: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null
) {
    val h = dimens().statusBarHeight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(h)
        // Фона нет намеренно. Затемняющий градиент был нужен, пока
        // полоса лежала поверх картинки машины и значки терялись
        // на светлых местах. Теперь она занимает свою высоту над
        // карточками, под ней чистый фон экрана — как у CC3.
    ) {
        TopStatusBar(
            weatherKey = weatherKey,
            onOpenShade = onOpenShade,
            onVoice = onVoice,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
        )
    }
}

/** Верхняя строка: Wi-Fi, дата, микрофон, BT, погода, громкость. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopStatusBar(
    modifier: Modifier = Modifier,
    weatherKey: Int = 0,
    onOpenShade: (() -> Unit)? = null,
    /** Нажатие на микрофон. null — помощник не поднялся. */
    onVoice: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }

    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { now = Date() }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(r, f)
        onDispose { runCatching { context.unregisterReceiver(r) } }
    }

    // Состояние модулей перечитываем по тику часов: пользователь мог
    // переключить их системной шторкой, пока лаунчер на экране.
    val wifiOn = remember(now) { QuickControls.isWifiOn(context) }
    val btOn = remember(now) { QuickControls.isBluetoothOn(context) }

    val fmt = remember { SimpleDateFormat("EE d.MM", Locale.getDefault()) }
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val volume = remember(now) {
        runCatching {
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            (cur * 30 / max)
        }.getOrDefault(0)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Времени здесь нет намеренно: оно уже есть в панели слева,
        // а у штатного лаунчера в этой строке только дата и значки.
        //
        // Значки кликабельные: раньше они были просто нарисованы, и по ним
        // нажимали впустую. Цвет показывает состояние — включённый модуль
        // белый, выключенный приглушённый.
        Icon(
            imageVector = if (wifiOn) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
            contentDescription = "Wi-Fi",
            tint = if (wifiOn) TextPrimary else TextSecondary,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable { QuickControls.openInternetPanel(context) }
        )

        Text(
            text = fmt.format(now).replaceFirstChar { it.uppercase() },
            color = TextPrimary,
            fontSize = 16.sp
        )

        Icon(
            Icons.Rounded.Mic, "Голосовой помощник",
            tint = TextPrimary,
            modifier = Modifier
                .size(21.dp)
                .clip(CircleShape)
                .clickable { onVoice?.invoke() }
        )

        // Bluetooth и батарея стоят вплотную одной группой — так у штатного
        // лаунчера. Между ними меньше зазор, чем между остальными значками,
        // и читаются они как одно целое «связь с телефоном».
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = if (btOn) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
                contentDescription = "Bluetooth",
                tint = if (btOn) TextPrimary else TextSecondary,
                modifier = Modifier
                    .size(21.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        // Короткое нажатие переключает, долгое открывает
                        // настройки: на части прошивок программное
                        // переключение закрыто, и нужен запасной путь.
                        onClick = { if (!QuickControls.toggleBluetooth(context)) QuickControls.openBluetoothSettings(context) },
                        onLongClick = { QuickControls.openBluetoothSettings(context) }
                    )
            )
            PhoneBattery()
        }

        // Питание автомобиля. Напряжение показываем, только если MCU
        // его действительно прислал: выдуманное число хуже пустого места.
        //
        // Цвет говорит о состоянии сам, без подписей: зелёный — идёт
        // зарядка от генератора, оранжевый — аккумулятор подсел,
        // красный — пора заводить или ставить на зарядку.
        val power by com.example.carlauncher.data.CarPower.rememberPower()
        if (power.hasVoltage) {
            val lvl = power.level
            val tint = when (lvl) {
                com.example.carlauncher.data.CarPower.Level.Charging -> Color(0xFF4CD07D)
                com.example.carlauncher.data.CarPower.Level.Good -> TextPrimary
                com.example.carlauncher.data.CarPower.Level.Low -> Color(0xFFFFB74D)
                else -> Color(0xFFFF5252)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    // Молния только когда генератор реально заряжает,
                    // иначе обычная батарея — иначе значок врёт.
                    if (lvl == com.example.carlauncher.data.CarPower.Level.Charging)
                        Icons.Rounded.BatteryChargingFull
                    else Icons.Rounded.BatteryFull,
                    contentDescription = "Бортовая сеть",
                    tint = tint,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "%.1fV".format(power.volts),
                    color = tint,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 3.dp)
                )
            }
        }

        // Реальная погода. Если сети или геолокации нет — блок скрыт.
        val weather by rememberWeather(weatherKey)
        if (weather.valid) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = weatherIcon(weather.code, weather.isDay),
                    contentDescription = weather.description,
                    tint = weatherTint(weather.code),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "${weather.tempC}°",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Громкость и стрелка шторки — одна серая пилюля, как в оригинале.
        // Раньше это были два отдельных кружка с зазором: выглядело
        // разболтанно, а у штатного они слиты в единый блок, где стрелка
        // сидит в светлом кружке внутри пилюли.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFF3A3E45))
                .padding(start = 11.dp, end = 3.dp, top = 3.dp, bottom = 3.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.VolumeUp, "Громкость",
                tint = TextPrimary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable {
                        runCatching {
                            am.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_SAME,
                                AudioManager.FLAG_SHOW_UI
                            )
                        }
                    }
            )
            Text(
                text = "$volume",
                color = TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 6.dp, end = 7.dp)
            )

            // Своя шторка по короткому нажатию, системная — по долгому:
            // уведомления Android иначе стали бы недоступны.
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF4F4F5))
                    .combinedClickable(
                        onClick = { onOpenShade?.invoke() ?: expandStatusBar(context) },
                        onLongClick = { expandStatusBar(context) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ExpandMore, "Шторка",
                    tint = Color(0xFF23262B),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/**
 * Заряд подключённого телефона.
 *
 * Уровень приходит по Bluetooth HFP и лежит в скрытом методе
 * getBatteryLevel — публичного способа его узнать нет. Метод есть
 * во всех версиях с Android 5, но если прошивка его закрыла или
 * телефон уровень не передаёт, значок просто не рисуем: пустая
 * батарейка вводила бы в заблуждение.
 */
@Composable
private fun PhoneBattery() {
    val context = LocalContext.current
    var level by remember { mutableStateOf(-1) }

    // Раз в минуту: чаще незачем, а держать подписку на профиль HFP
    // ради значка — лишний фоновый сервис.
    LaunchedEffect(Unit) {
        while (true) {
            level = com.example.carlauncher.data.BtDevice.batteryLevel(context)
            kotlinx.coroutines.delay(60_000)
        }
    }

    if (level !in 0..100) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 11.dp, height = 19.dp)) {
            val capW = size.width * 0.42f
            val capH = size.height * 0.09f
            val bodyTop = capH
            val stroke = size.width * 0.11f

            // Носик сверху
            drawRoundRect(
                color = Color(0xFFE8ECF8),
                topLeft = Offset((size.width - capW) / 2f, 0f),
                size = Size(capW, capH * 1.6f),
                cornerRadius = CornerRadius(stroke)
            )
            // Корпус
            drawRoundRect(
                color = Color(0xFFE8ECF8),
                topLeft = Offset(0f, bodyTop),
                size = Size(size.width, size.height - bodyTop),
                cornerRadius = CornerRadius(size.width * 0.22f),
                style = Stroke(width = stroke)
            )
            // Заливка снизу вверх — зелёная, как в оригинале
            val innerPad = stroke * 1.6f
            val innerTop = bodyTop + innerPad
            val innerH = size.height - innerTop - innerPad
            val fillH = innerH * (level / 100f)
            if (fillH > 0f) {
                drawRoundRect(
                    color = if (level <= 15) Color(0xFFE05B4B) else Color(0xFF14A32E),
                    topLeft = Offset(innerPad, innerTop + (innerH - fillH)),
                    size = Size(size.width - innerPad * 2, fillH),
                    cornerRadius = CornerRadius(stroke * 0.7f)
                )
            }
        }
    }
}

/** Открывает системную шторку через скрытый StatusBarManager. */
private fun expandStatusBar(context: Context) {
    runCatching {
        val sb = context.getSystemService("statusbar")
        val cls = Class.forName("android.app.StatusBarManager")
        val method = cls.getMethod("expandNotificationsPanel")
        method.invoke(sb)
    }
}

/** Иконка по коду WMO: ясно, облачно, дождь, снег, гроза, туман. */
@Composable
fun weatherIcon(code: Int, isDay: Boolean): androidx.compose.ui.graphics.vector.ImageVector = when (code) {
    0 -> if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightlightRound
    1, 2 -> Icons.Rounded.CloudQueue
    3 -> Icons.Rounded.Cloud
    45, 48 -> Icons.Rounded.Grain
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Rounded.WaterDrop
    71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit
    95, 96, 99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

/** Цвет иконки погоды в акцентах активной темы. */
@Composable
fun weatherTint(code: Int): Color = when (code) {
    0 -> Color(0xFFFFC64D)
    71, 73, 75, 77, 85, 86 -> Color(0xFFB8E4FF)
    95, 96, 99 -> Color(0xFFFFD166)
    else -> TextSecondary
}
