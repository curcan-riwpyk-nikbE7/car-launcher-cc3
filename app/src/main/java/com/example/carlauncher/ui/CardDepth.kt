package com.example.carlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Глубина карточки: тень цветом акцента и подсветка верхней грани.
 *
 * Карточки лежали на фоне совершенно плоско — отличались от него
 * только заливкой, и на тёмной теме граница почти терялась. У штатного
 * интерфейса под карточками есть мягкое цветное свечение, из-за
 * которого они выглядят приподнятыми.
 *
 * Тень намеренно цветная, а не чёрная: на тёмно-синем фоне чёрная
 * тень не видна вовсе, а лёгкий акцентный ореол читается и добавляет
 * тот самый неоновый вид.
 *
 * Верхняя грань подсвечена чуть светлее нижней — имитация света,
 * падающего сверху. Приём старый, но именно он не даёт карточке
 * выглядеть наклейкой.
 */
@Composable
fun Modifier.cardDepth(
    corner: Dp,
    accent: Color,
    background: Color,
    stroke: Color,
    strokeWidth: Dp = 1.dp,
    elevation: Dp = 8.dp
): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            // Тень окрашена акцентом: чёрная на тёмном фоне невидима
            ambientColor = accent.copy(alpha = 0.5f),
            spotColor = accent.copy(alpha = 0.5f)
        )
        .clip(shape)
        .background(background)
        // Свет сверху: верхняя кромка светлее, нижняя почти прозрачная
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.05f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.06f)
                )
            )
        )
        .border(strokeWidth, stroke, shape)
}
