package com.example.carlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.carlauncher.data.SettingsStore

/**
 * Диалог выбора того, что именно показывать в правой карточке:
 * Спидометр, GPS-карту, Виджет (Яндекс Музыка и др.) или YouTube.
 */
@Composable
fun CardContentModeDialog(
    currentMode: String,
    onSelectMode: (String) -> Unit,
    onPickAppForSpeed: () -> Unit,
    onPickWidget: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalThemeSpec.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(s.cardBg)
                .padding(22.dp)
        ) {
            Text(
                text = "Что показывать в карточке?",
                color = s.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = s.fontFamily
            )
            Text(
                text = "Выберите режим отображения вместо классического спидометра",
                color = s.textSecondary,
                fontSize = 12.sp,
                fontFamily = s.fontFamily,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeItem(
                    icon = Icons.Rounded.Speed,
                    title = "Спидометр",
                    subtitle = "Классический спидометр CC3 с автомобилем",
                    selected = currentMode == SettingsStore.CARD_MODE_SPEED,
                    onClick = {
                        onSelectMode(SettingsStore.CARD_MODE_SPEED)
                        onDismiss()
                    }
                )

                ModeItem(
                    icon = Icons.Rounded.Map,
                    title = "Карта",
                    subtitle = "Навигация (Яндекс Навигатор, Яндекс Карты, 2ГИС)",
                    selected = currentMode == SettingsStore.CARD_MODE_MAP || currentMode == SettingsStore.CARD_MODE_EMBEDDED,
                    onClick = {
                        onSelectMode(SettingsStore.CARD_MODE_MAP)
                        onDismiss()
                    }
                )

                ModeItem(
                    icon = Icons.Rounded.PlayCircle,
                    title = "YouTube",
                    subtitle = "Нативное приложение YouTube прямо в карточке",
                    selected = currentMode == SettingsStore.CARD_MODE_YOUTUBE,
                    onClick = {
                        onSelectMode(SettingsStore.CARD_MODE_YOUTUBE)
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Дополнительный пункт — настроить приложение для быстрого клика
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable {
                        onDismiss()
                        onPickAppForSpeed()
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = s.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Назначить приложение по тапу",
                        color = s.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = s.fontFamily
                    )
                    Text(
                        text = "Что открывать при нажатии на спидометр",
                        color = s.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = s.fontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val s = LocalThemeSpec.current
    val bgColor = if (selected) s.accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f)
    val iconTint = if (selected) s.accent else s.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) s.accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (selected) s.accent else s.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = s.fontFamily
            )
            Text(
                text = subtitle,
                color = s.textSecondary,
                fontSize = 11.sp,
                fontFamily = s.fontFamily
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = s.accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
