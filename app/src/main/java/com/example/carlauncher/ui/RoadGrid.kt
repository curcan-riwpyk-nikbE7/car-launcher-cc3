package com.example.carlauncher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Бегущая перспективная сетка под машиной.
 *
 * Раньше пробовали анимировать саму машину — покачивание, наклон
 * по акселерометру. Получилось хуже статичной картинки: изображение
 * дёргалось и выглядело дёшево, пользователь попросил вернуть как было.
 *
 * Здесь приём другой, тот, что используют штатные лаунчеры: машина
 * стоит неподвижно, а дорога под ней ползёт. Мозг достраивает движение
 * сам, и картинка при этом остаётся чистой.
 *
 * Рисуется на Canvas поверх фона: ни одного нового файла в APK.
 */
@Composable
fun RoadGrid(
    speedKmh: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    // Ниже 3 км/ч не анимируем: GPS на стоянке шумит, и сетка
    // ползла бы у неподвижной машины.
    if (speedKmh < 3) return

    // Период одного шага: чем быстрее едем, тем короче. Границы
    // подобраны так, чтобы на 200 км/ч не превращалось в мельтешение.
    val periodMs = (2400 - speedKmh * 9).coerceIn(320, 2400)

    val t = rememberInfiniteTransition(label = "road")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Линия горизонта: выше неё сетки нет. Берём чуть выше середины —
        // так же, как на исходной картинке.
        val horizon = h * 0.42f
        val depth = h - horizon
        if (depth <= 0f) return@Canvas

        val rows = 9
        for (i in 0 until rows) {
            // Позиция ряда 0..1 с учётом фазы — ряды непрерывно
            // «выезжают» из горизонта вниз.
            val p = ((i + phase) / rows).coerceIn(0f, 1f)

            // Перспектива: ближние ряды расходятся быстрее дальних.
            // Степень 2.6 подобрана на глаз под пропорции карточки —
            // при линейном распределении сетка выглядит плоской.
            val k = p.pow(2.6f)
            val y = horizon + depth * k

            // Дальние ряды бледнее: имитация тумана даёт глубину
            // и прячет момент появления ряда на горизонте.
            val alpha = (k * 0.55f).coerceIn(0f, 0.55f)
            if (alpha < 0.02f) continue

            // Поперечная линия
            val halfWidth = w * (0.08f + k * 0.62f)
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(w / 2f - halfWidth, y),
                end = Offset(w / 2f + halfWidth, y),
                strokeWidth = 1f + k * 2.2f
            )
        }

        // Продольные линии, сходящиеся к точке схода. Они не двигаются —
        // движение целиком на поперечных, иначе рябит в глазах.
        val vanishX = w / 2f
        for (lane in -3..3) {
            if (lane == 0) continue
            val bottomX = vanishX + lane * w * 0.20f
            drawLine(
                color = color.copy(alpha = 0.16f),
                start = Offset(vanishX + lane * w * 0.012f, horizon),
                end = Offset(bottomX, h),
                strokeWidth = 1.2f
            )
        }
    }
}
