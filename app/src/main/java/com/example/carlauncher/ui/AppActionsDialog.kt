package com.example.carlauncher.ui

import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.R
import com.example.carlauncher.data.AppInfo

/**
 * Действия с приложением по долгому нажатию в списке.
 * Стандартное поведение любого лаунчера, которого здесь не хватало.
 */
@Composable
fun AppActionsDialog(
    app: AppInfo,
    isSystem: Boolean,
    onAddToFavorites: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    /** Убрать из меню, не удаляя с устройства. */
    onHide: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val s = LocalThemeSpec.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = s.cardBg,
        shape = RoundedCornerShape(s.cardCorner),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(s.iconCorner))
                        .background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(app.icon, app.label, Modifier.size(30.dp))
                }
                Text(
                    text = app.label,
                    color = s.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = s.fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionLine(Icons.Rounded.PushPin, stringResource(R.string.add_to_favorites), "Добавить на главный экран", onAddToFavorites)
                ActionLine(Icons.Rounded.Info, stringResource(R.string.app_info), "Разрешения, память, отключение", onAppInfo)
                onHide?.let {
                    ActionLine(
                        Icons.Rounded.VisibilityOff,
                        "Скрыть из меню",
                        "Останется на устройстве, вернуть — в настройках",
                        it
                    )
                }
                ActionLine(
                    icon = Icons.Rounded.Delete,
                    title = if (isSystem) stringResource(R.string.disable) else stringResource(R.string.uninstall),
                    subtitle = if (isSystem) "Системное приложение нельзя удалить"
                               else "Запрос на удаление",
                    onClick = onUninstall,
                    danger = !isSystem
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = s.textSecondary, fontFamily = s.fontFamily)
            }
        }
    )
}

@Composable
private fun ActionLine(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val s = LocalThemeSpec.current
    val tint = if (danger) Color(0xFFFF5A5A) else s.textPrimary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(s.iconCorner))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 8.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(title, color = tint, fontSize = 15.sp, fontFamily = s.fontFamily)
            Text(subtitle, color = s.textSecondary, fontSize = 11.sp, fontFamily = s.fontFamily)
        }
    }
}
