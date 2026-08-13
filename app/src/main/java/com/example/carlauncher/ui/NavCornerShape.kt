package com.example.carlauncher.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Форма кнопки навигации: диагональный срез сверху и скруглённый низ.
 *
 * Верхняя грань идёт по диагонали слева-вниз направо-вверх, как на
 * оригинальной панели — кнопка будто «вырезана» из угла экрана.
 *
 * @param diagonal доля высоты, которую занимает диагональный скос
 * @param corner радиус скругления нижних углов в пикселях
 */
class NavCornerShape(
    private val diagonal: Float = 0.42f,
    private val corner: Float = 44f
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val cut = h * diagonal
        val r = corner.coerceAtMost(minOf(w, h) / 2f)

        val path = Path().apply {
            // Левый верхний угол — самая низкая точка диагонали
            moveTo(0f, cut)
            // Диагональ вверх-вправо
            lineTo(w, 0f)
            // Правая грань вниз до скругления
            lineTo(w, h - r)
            quadraticBezierTo(w, h, w - r, h)
            // Нижняя грань влево
            lineTo(r, h)
            quadraticBezierTo(0f, h, 0f, h - r)
            close()
        }
        return Outline.Generic(path)
    }
}
