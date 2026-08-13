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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.carlauncher.data.AppInfo

/** Диалог выбора приложения для плитки (тап по пустой ячейке или долгое нажатие). */
@Composable
fun AppPickerDialog(
    apps: List<AppInfo>,
    title: String,
    onPick: (AppInfo) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBgSoft,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, color = TextPrimary, style = MaterialTheme.typography.titleLarge) },
        text = {
            Box(modifier = Modifier.height(320.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(apps, key = { it.packageName + it.activityName }) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPick(app) }
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcon(app.icon, app.label, Modifier.size(26.dp))
                            }
                            Column(modifier = Modifier.padding(start = 14.dp)) {
                                Text(app.label, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onReset) { Text("Очистить", color = Cyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = TextSecondary) } }
    )
}
