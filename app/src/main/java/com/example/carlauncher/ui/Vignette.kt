package com.example.carlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Мягкое свечение по углам экрана.
 *
 * Плоский вертикальный градиент фона выглядел «бумажно»: у оригинального
 * интерфейса CC3 свет собирается в углах, и от этого экран кажется
 * глубже, а карточки — приподнятыми над фоном.
 *
 * Свет намеренно приглушён относительно рекламных скриншотов. Там его
 * выкручивают на максимум, но реклама рассматривается на телефоне
 * в комнате, а магнитола — днём под прямым солнцем. Яркое пятно в углу
 * при таком свете превращается в блик и мешает читать статус-бар.
 *
 * Рисуется поверх фона, но под карточками, и не перехватывает касания.
 */
@Composable
fun Vignette(
    accent: Color,
    accent2: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Верхний левый угол — там панель с кольцом ассистента,
        // подсветка делает её центром внимания.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(w * 0.06f, h * 0.10f),
                radius = w * 0.42f
            ),
            size = size
        )

        // Правый нижний — уравновешивает композицию по диагонали.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(accent2.copy(alpha = 0.13f), Color.Transparent),
                center = Offset(w * 0.94f, h * 0.92f),
                radius = w * 0.40f
            ),
            size = size
        )

        // Затемнение по краям. Без него подсветка углов выглядит
        // как грязное пятно, а не как источник света: глазу нужен
        // контраст между светлым центром и тёмной рамкой.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                center = Offset(w / 2f, h / 2f),
                radius = w * 0.72f
            ),
            size = size
        )
    }
}
