package com.example.carlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.NowPlaying

/**
 * Флагманская медиа-карточка в стиле TEYES CC3 / TEYES.PRO:
 * Крупная квадратная обложка альбома, название песни, артист, лайк (сердечко),
 * большие автомобильные кнопки управления треками.
 */
@Composable
fun TeyesMediaCard(
    state: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    var isLiked by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(s.cardBg)
            .border(s.strokeWidth, s.cardStroke, RoundedCornerShape(s.cardCorner))
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Большая квадратная обложка альбома с закругленными углами
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(onClick = onOpenPlayer),
            contentAlignment = Alignment.Center
        ) {
            if (state.artwork != null) {
                Image(
                    bitmap = state.artwork.asImageBitmap(),
                    contentDescription = state.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = s.accent.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 2. Название трека, артист и кнопка лайка (сердечко)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = onOpenPlayer)) {
                Text(
                    text = state.title.ifBlank { "Яндекс Музыка" },
                    color = s.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = s.fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.artist.ifBlank { "Нажмите для запуска плеера" },
                    color = s.textSecondary,
                    fontSize = 13.sp,
                    fontFamily = s.fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "В избранное",
                tint = if (isLiked) Color(0xFFFF3366) else s.textDim,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { isLiked = !isLiked }
                    .padding(2.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Фирменная надпись TEYES.PRO
        Text(
            text = "TEYES.PRO",
            color = s.accent.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            fontFamily = s.fontFamily,
            modifier = Modifier.padding(vertical = 2.dp)
        )

        Spacer(Modifier.height(8.dp))

        // 3. Крупные сенсорные кнопки управления воспроизведением
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "Предыдущий",
                tint = s.textPrimary,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPrev)
                    .padding(6.dp)
            )

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Воспроизведение",
                    tint = s.accent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Следующий",
                tint = s.textPrimary,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNext)
                    .padding(6.dp)
            )

            Icon(
                Icons.Rounded.Repeat,
                contentDescription = "Повтор",
                tint = s.textDim,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { }
                    .padding(6.dp)
            )
        }
    }
}
