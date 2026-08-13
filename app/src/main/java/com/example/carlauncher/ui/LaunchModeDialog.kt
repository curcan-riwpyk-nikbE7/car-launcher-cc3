package com.example.carlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Что делает карточка спидометра при нажатии.
 *
 * Вариант «внутри карточки» убран: Android не разрешает рисовать
 * чужое приложение в своём окне. Вместо него разделённый экран —
 * штатный способ показать лаунчер и приложение одновременно.
 */
@Composable
fun LaunchModeDialog(
    currentApp: String?,
    freeformAvailable: Boolean = false,
    currentArea: String = "RightColumn",
    onAreaChange: (String) -> Unit = {},
    onPickEmbed: () -> Unit = {},
    onPickFreeform: () -> Unit = {},
    onPickVideo: () -> Unit = {},
    onPickSplit: () -> Unit,
    onPickFullscreen: () -> Unit,
    onClearApp: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalThemeSpec.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = s.cardBg,
        shape = RoundedCornerShape(s.cardCorner),
        title = {
            Column {
                Text(
                    "Карточка спидометра",
                    color = s.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = s.fontFamily
                )
                if (currentApp != null) {
                    Text(
                        "Сейчас: $currentApp",
                        color = s.accent,
                        fontSize = 12.sp,
                        fontFamily = s.fontFamily,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Первым пунктом — единственный способ показать видео
                // прямо в карточке без прав прошивки.
                ModeRow(
                    icon = Icons.Rounded.SmartDisplay,
                    title = "Видео в карточке",
                    subtitle = "Свой список роликов, играет внутри лаунчера",
                    onClick = onPickVideo
                )
                ModeRow(
                    icon = Icons.Rounded.Dashboard,
                    title = "Встроить в карточку",
                    subtitle = "Без рамок: приложение станет частью лаунчера",
                    onClick = onPickEmbed
                )
                ModeRow(
                    icon = Icons.Rounded.PictureInPicture,
                    title = "В плавающем окне",
                    subtitle = if (freeformAvailable)
                        "Приложение рядом с лаунчером, размер ниже"
                    else
                        "Нужен режим плавающих окон — см. подсказку ниже",
                    onClick = onPickFreeform
                )

                // Размер окна решает всё: в тесной области приложения
                // вроде YouTube пытаются впихнуть полный интерфейс
                // и выглядят месивом.
                if (freeformAvailable) {
                    Text(
                        text = "Размер окна приложения",
                        color = s.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = s.fontFamily,
                        modifier = Modifier.padding(start = 14.dp, top = 6.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        listOf(
                            "Card" to "Карточка",
                            "RightColumn" to "Колонка",
                            "RightHalf" to "Крупно"
                        ).forEach { (key, label) ->
                            val sel = key == currentArea
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(s.buttonCorner))
                                    .background(
                                        if (sel) s.accent else s.textPrimary.copy(alpha = 0.07f)
                                    )
                                    .clickable { onAreaChange(key) }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (sel) s.onAccent else s.textSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = s.fontFamily
                                )
                            }
                        }
                    }
                }
                ModeRow(
                    icon = Icons.Rounded.VerticalSplit,
                    title = "Открыть рядом",
                    subtitle = "Лаунчер слева, приложение справа — карта, YouTube, что угодно",
                    onClick = onPickSplit
                )
                ModeRow(
                    icon = Icons.Rounded.OpenInFull,
                    title = "На весь экран",
                    subtitle = "Приложение занимает экран целиком",
                    onClick = onPickFullscreen
                )
                if (!freeformAvailable) {
                    Text(
                        text = "Для режима «в карточке» один раз выполните:\n" +
                            "adb shell settings put global enable_freeform_support 1\n" +
                            "Root не нужен.",
                        color = s.textDim,
                        fontSize = 10.sp,
                        fontFamily = s.fontFamily,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                if (currentApp != null) {
                    ModeRow(
                        icon = Icons.Rounded.Speed,
                        title = "Вернуть спидометр",
                        subtitle = "Убрать приложение с карточки",
                        onClick = onClearApp
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = s.textSecondary, fontFamily = s.fontFamily)
            }
        }
    )
}

@Composable
private fun ModeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val s = LocalThemeSpec.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(s.iconCorner))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(s.iconCorner))
                .background(s.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = s.accent, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(title, color = s.textPrimary, fontSize = 15.sp, fontFamily = s.fontFamily)
            Text(subtitle, color = s.textSecondary, fontSize = 11.sp, fontFamily = s.fontFamily)
        }
    }
}
