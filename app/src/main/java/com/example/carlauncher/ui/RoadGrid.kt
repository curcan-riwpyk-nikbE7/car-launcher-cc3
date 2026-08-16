package com.example.carlauncher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.carlauncher.R

/**
 * Движение дороги под машиной — покадровая анимация.
 *
 * Две первые попытки были неверными, и обе поучительные.
 *
 * Сначала я рисовал свою сетку линиями поверх картинки. Но на ней уже
 * есть готовая сетка из точек, со своей перспективой и цветом: две
 * сетки накладывались и давали кашу поперёк кузова.
 *
 * Потом масштабировал картинку от точки схода. Движение появилось,
 * но «дышало» всё изображение целиком, включая дальний план.
 *
 * Как оказалось, штатный лаунчер не вычисляет движение вовсе —
 * он листает 30 заранее нарисованных кадров. Никакой математики:
 * художник нарисовал цикл, дальние ряды в нём смещаются меньше
 * ближних, и перспектива получается сама собой.
 *
 * Кадры лежат отдельным слоем с прозрачностью и ложатся поверх
 * статичной картинки — машина при этом остаётся неподвижной.
 */
@Composable
fun RoadGrid(
    speedKmh: Int,
    tint: ColorFilter?,
    modifier: Modifier = Modifier
) {
    // Ниже 3 км/ч не анимируем: GPS на стоянке шумит и показывает
    // 1-2 км/ч, дорога ползла бы под неподвижной машиной.
    if (speedKmh < 3) return

    val frames = remember { FRAMES }

    // Длительность полного цикла. На 200 км/ч не должно превращаться
    // в мельтешение, поэтому нижняя граница 900 мс — это 33 кадра/с,
    // быстрее человеческий глаз всё равно не различает.
    val periodMs = (4000 - speedKmh * 15).coerceIn(900, 4000)

    val t = rememberInfiniteTransition(label = "road")
    val frame by t.animateFloat(
        initialValue = 0f,
        targetValue = frames.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "frame"
    )

    val index = frame.toInt().coerceIn(0, frames.lastIndex)

    Image(
        painter = painterResource(frames[index]),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        colorFilter = tint,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Кадры цикла. Держим списком, а не собираем имя строкой:
 * getIdentifier по имени работает через рефлексию, и R8 в релизе
 * вырезает такие ресурсы как неиспользуемые.
 */
private val FRAMES = intArrayOf(
    R.drawable.road_00, R.drawable.road_01, R.drawable.road_02,
    R.drawable.road_03, R.drawable.road_04, R.drawable.road_05,
    R.drawable.road_06, R.drawable.road_07, R.drawable.road_08,
    R.drawable.road_09, R.drawable.road_10, R.drawable.road_11,
    R.drawable.road_12, R.drawable.road_13, R.drawable.road_14,
    R.drawable.road_15, R.drawable.road_16, R.drawable.road_17,
    R.drawable.road_18, R.drawable.road_19, R.drawable.road_20,
    R.drawable.road_21, R.drawable.road_22, R.drawable.road_23,
    R.drawable.road_24, R.drawable.road_25, R.drawable.road_26,
    R.drawable.road_27, R.drawable.road_28, R.drawable.road_29
)
