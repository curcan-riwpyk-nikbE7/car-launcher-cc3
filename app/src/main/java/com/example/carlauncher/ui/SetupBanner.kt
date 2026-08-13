package com.example.carlauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.R

/**
 * Баннер «сделайте лаунчер главным».
 *
 * Появляется, только если система запускает по кнопке «Домой» не нас.
 * Скрывается сразу после назначения — постоянно висеть не будет.
 */
@Composable
fun SetupBanner(
    visible: Boolean,
    onFix: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(s.cardCorner))
                .background(s.cardBg)
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(listOf(s.accent, s.accent2)),
                    shape = RoundedCornerShape(s.cardCorner)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(s.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Home, null, tint = s.accent, modifier = Modifier.size(21.dp))
            }

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.setup_title),
                    color = s.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = s.fontFamily
                )
                Text(
                    text = stringResource(R.string.setup_subtitle),
                    color = s.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = s.fontFamily
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(s.buttonCorner))
                    .background(s.accent)
                    .clickable(onClick = onFix)
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    text = stringResource(R.string.setup_action),
                    color = s.onAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = s.fontFamily
                )
            }

            Icon(
                Icons.Rounded.Close, stringResource(R.string.close),
                tint = s.textDim,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(7.dp)
            )
        }
    }
}
