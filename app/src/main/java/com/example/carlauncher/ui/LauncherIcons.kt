package com.example.carlauncher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Собственные контурные иконки панели.
 *
 * Material-иконки здесь не подошли: у них заливка и другая геометрия,
 * а на эталонной панели иконки нарисованы тонкой линией со скруглёнными
 * стыками. Рисуем сами, чтобы попасть в стиль.
 */
object LauncherIcons {

    /** Шестерня с шестью скруглёнными лепестками. */
    val Gear: ImageVector by lazy {
        ImageVector.Builder(
            name = "LauncherGear",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero
            ) {
                // Внешний «цветок»: шесть выступов, соединённых дугами
                moveTo(12f, 2.6f)
                curveTo(13.3f, 2.6f, 14.3f, 3.7f, 14.2f, 5f)
                curveTo(14.15f, 5.75f, 14.55f, 6.45f, 15.2f, 6.8f)
                curveTo(15.85f, 7.15f, 16.65f, 7.1f, 17.25f, 6.65f)
                curveTo(18.3f, 5.9f, 19.75f, 6.15f, 20.4f, 7.28f)
                curveTo(21.05f, 8.4f, 20.6f, 9.8f, 19.45f, 10.35f)
                curveTo(18.8f, 10.66f, 18.35f, 11.3f, 18.35f, 12f)
                curveTo(18.35f, 12.7f, 18.8f, 13.34f, 19.45f, 13.65f)
                curveTo(20.6f, 14.2f, 21.05f, 15.6f, 20.4f, 16.72f)
                curveTo(19.75f, 17.85f, 18.3f, 18.1f, 17.25f, 17.35f)
                curveTo(16.65f, 16.9f, 15.85f, 16.85f, 15.2f, 17.2f)
                curveTo(14.55f, 17.55f, 14.15f, 18.25f, 14.2f, 19f)
                curveTo(14.3f, 20.3f, 13.3f, 21.4f, 12f, 21.4f)
                curveTo(10.7f, 21.4f, 9.7f, 20.3f, 9.8f, 19f)
                curveTo(9.85f, 18.25f, 9.45f, 17.55f, 8.8f, 17.2f)
                curveTo(8.15f, 16.85f, 7.35f, 16.9f, 6.75f, 17.35f)
                curveTo(5.7f, 18.1f, 4.25f, 17.85f, 3.6f, 16.72f)
                curveTo(2.95f, 15.6f, 3.4f, 14.2f, 4.55f, 13.65f)
                curveTo(5.2f, 13.34f, 5.65f, 12.7f, 5.65f, 12f)
                curveTo(5.65f, 11.3f, 5.2f, 10.66f, 4.55f, 10.35f)
                curveTo(3.4f, 9.8f, 2.95f, 8.4f, 3.6f, 7.28f)
                curveTo(4.25f, 6.15f, 5.7f, 5.9f, 6.75f, 6.65f)
                curveTo(7.35f, 7.1f, 8.15f, 7.15f, 8.8f, 6.8f)
                curveTo(9.45f, 6.45f, 9.85f, 5.75f, 9.8f, 5f)
                curveTo(9.7f, 3.7f, 10.7f, 2.6f, 12f, 2.6f)
                close()
            }
            // Центральное кольцо
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15f, 12f)
                curveTo(15f, 13.66f, 13.66f, 15f, 12f, 15f)
                curveTo(10.34f, 15f, 9f, 13.66f, 9f, 12f)
                curveTo(9f, 10.34f, 10.34f, 9f, 12f, 9f)
                curveTo(13.66f, 9f, 15f, 10.34f, 15f, 12f)
                close()
            }
        }.build()
    }

    /** Изометрический куб — «все приложения». */
    val Cube: ImageVector by lazy {
        ImageVector.Builder(
            name = "LauncherCube",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            // Внешний шестиугольник
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2.8f)
                lineTo(20.2f, 7.4f)
                lineTo(20.2f, 16.6f)
                lineTo(12f, 21.2f)
                lineTo(3.8f, 16.6f)
                lineTo(3.8f, 7.4f)
                close()
            }
            // Три внутренних ребра, сходящихся в центре
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3.8f, 7.4f)
                lineTo(12f, 12f)
                lineTo(20.2f, 7.4f)
                moveTo(12f, 12f)
                lineTo(12f, 21.2f)
            }
        }.build()
    }

    /** Стрелка навигации — «бумажный самолётик». */
    val NavArrow: ImageVector by lazy {
        ImageVector.Builder(
            name = "LauncherNavArrow",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20.5f, 3.5f)
                lineTo(3.5f, 10.6f)
                lineTo(10.4f, 13.6f)
                lineTo(13.4f, 20.5f)
                close()
                moveTo(10.4f, 13.6f)
                lineTo(20.5f, 3.5f)
            }
        }.build()
    }

    /** Ползунки — «настройки лаунчера». */
    val Sliders: ImageVector by lazy {
        ImageVector.Builder(
            name = "LauncherSliders",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Три горизонтальные линии
                moveTo(3.5f, 7f); lineTo(20.5f, 7f)
                moveTo(3.5f, 12f); lineTo(20.5f, 12f)
                moveTo(3.5f, 17f); lineTo(20.5f, 17f)
            }
            // Ручки на линиях
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.4f, 7f)
                curveTo(10.4f, 8f, 9.6f, 8.8f, 8.6f, 8.8f)
                curveTo(7.6f, 8.8f, 6.8f, 8f, 6.8f, 7f)
                curveTo(6.8f, 6f, 7.6f, 5.2f, 8.6f, 5.2f)
                curveTo(9.6f, 5.2f, 10.4f, 6f, 10.4f, 7f)
                close()
                moveTo(17.2f, 12f)
                curveTo(17.2f, 13f, 16.4f, 13.8f, 15.4f, 13.8f)
                curveTo(14.4f, 13.8f, 13.6f, 13f, 13.6f, 12f)
                curveTo(13.6f, 11f, 14.4f, 10.2f, 15.4f, 10.2f)
                curveTo(16.4f, 10.2f, 17.2f, 11f, 17.2f, 12f)
                close()
                moveTo(10.4f, 17f)
                curveTo(10.4f, 18f, 9.6f, 18.8f, 8.6f, 18.8f)
                curveTo(7.6f, 18.8f, 6.8f, 18f, 6.8f, 17f)
                curveTo(6.8f, 16f, 7.6f, 15.2f, 8.6f, 15.2f)
                curveTo(9.6f, 15.2f, 10.4f, 16f, 10.4f, 17f)
                close()
            }
        }.build()
    }
}
