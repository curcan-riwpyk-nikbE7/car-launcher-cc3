package com.example.carlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.rememberWeather
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Верхняя строка: Wi-Fi, дата, микрофон, BT, погода, громкость. */
@Composable
fun TopStatusBar(modifier: Modifier = Modifier, weatherKey: Int = 0) {
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
        Icon(Icons.Rounded.Wifi, "Wi-Fi", tint = TextPrimary, modifier = Modifier.size(19.dp))

        Text(
            text = fmt.format(now).replaceFirstChar { it.uppercase() },
            color = TextPrimary,
            fontSize = 16.sp
        )

        Icon(Icons.Rounded.Mic, "Голос", tint = TextPrimary, modifier = Modifier.size(18.dp))
        Icon(Icons.Rounded.Bluetooth, "Bluetooth", tint = Cyan, modifier = Modifier.size(18.dp))

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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .clickable {
                    runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI) }
                }
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Громкость", tint = TextPrimary, modifier = Modifier.size(15.dp))
            Text(" $volume", color = TextPrimary, fontSize = 13.sp)
        }

        Icon(
            Icons.Rounded.ExpandMore, "Шторка",
            tint = TextPrimary,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .clickable { expandStatusBar(context) }
                .padding(3.dp)
        )
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
