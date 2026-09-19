package com.example.carlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.AppInfo

/**
 * Диалог выбора приложения для плитки (тап по пустой ячейке или долгое нажатие).
 * В самое начало списка добавлены специальные системные функции:
 * - «Все приложения» (меню установленных программ)
 * - «Настройки лаунчера»
 */
@Composable
fun AppPickerDialog(
    apps: List<AppInfo>,
    title: String,
    onPick: (AppInfo) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalThemeSpec.current

    val builtInActions = remember {
        listOf(
            AppInfo(
                label = "Все приложения",
                packageName = "builtin:all_apps",
                activityName = "all_apps",
                icon = null
            ),
            AppInfo(
                label = "Настройки лаунчера",
                packageName = "builtin:settings",
                activityName = "settings",
                icon = null
            )
        )
    }

    val fullList = remember(apps) { builtInActions + apps }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = s.cardBg,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, color = s.textPrimary, style = MaterialTheme.typography.titleLarge) },
        text = {
            Box(modifier = Modifier.height(340.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(fullList, key = { it.packageName + "/" + it.activityName }) { app ->
                        val isBuiltIn = app.packageName.startsWith("builtin:")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPick(app) }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isBuiltIn) s.accent.copy(alpha = 0.18f)
                                        else Color.White.copy(alpha = 0.05f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when (app.packageName) {
                                    "builtin:all_apps" -> {
                                        Icon(
                                            Icons.Rounded.Apps,
                                            contentDescription = app.label,
                                            tint = s.accent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    "builtin:settings" -> {
                                        Icon(
                                            Icons.Rounded.Settings,
                                            contentDescription = app.label,
                                            tint = s.accent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    else -> {
                                        AppIcon(app.icon, app.label, Modifier.size(28.dp))
                                    }
                                }
                            }
                            Column(modifier = Modifier.padding(start = 14.dp)) {
                                Text(
                                    text = app.label,
                                    color = if (isBuiltIn) s.accent else s.textPrimary,
                                    fontWeight = if (isBuiltIn) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 15.sp
                                )
                                if (isBuiltIn) {
                                    Text(
                                        text = "Функция лаунчера",
                                        color = s.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onReset) { Text("Очистить", color = s.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = s.textSecondary) } }
    )
}
