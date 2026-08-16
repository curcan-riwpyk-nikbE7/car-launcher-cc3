package com.example.carlauncher.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.R
import com.example.carlauncher.data.AppInfo

/**
 * Карточка авто. Внешний вид сильно зависит от темы: фото машины может
 * скрываться, а скорость рисоваться крупными цифрами, тонким шрифтом
 * или аналоговым кольцом.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarCard(
    speedKmh: Int,
    showSpeed: Boolean = true,
    /** Приложение, назначенное на виджет спидометра (null — не назначено). */
    speedApp: AppInfo? = null,
    /** Короткий тап по спидометру. */
    onSpeedClick: () -> Unit = {},
    /** Долгое удержание спидометра — выбрать приложение или виджет. */
    onSpeedLongClick: () -> Unit = {},
    /** Сообщает фактические границы карточки на экране в пикселях. */
    onBounds: (android.graphics.Rect) -> Unit = {},
    /** Пакет приложения, встроенного прямо в карточку (null — спидометр). */
    embeddedPackage: String? = null,
    onEmbedFailed: () -> Unit = {},
    onClimate: () -> Unit,
    onLights: () -> Unit,
    onExpand: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    val cardHaptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(s.carCardBg)
            .border(s.strokeWidth, s.cardStroke, RoundedCornerShape(s.cardCorner))
            .combinedClickable(
                onClick = { },
                onLongClick = {
                    cardHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onExpand()
                }
            )
            // Координаты нужны, чтобы плавающее окно приложения
            // легло ровно в границы этой карточки.
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val sz = coords.size
                onBounds(
                    android.graphics.Rect(
                        pos.x.toInt(),
                        pos.y.toInt(),
                        pos.x.toInt() + sz.width,
                        pos.y.toInt() + sz.height
                    )
                )
            }
    ) {
        if (s.showCarImage) {
            // Картинка показывается как есть, без анимаций.
            // Покачивание, наклон по акселерометру и бегущие полосы
            // убраны намеренно: пользователь хочет исходный вид,
            // где машина стоит ровно и ничего поверх неё не рисуется.
            // Картинка одна на все темы, цвет даёт фильтр — так не нужно
            // держать в APK четыре копии одного изображения.
            // Тона переводим в яркость и красим акцентом темы.
            val tint = if (s.tintCar) {
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            // строки R,G,B берут яркость (0.30/0.42/0.28)
                            // и умножают её на компоненту акцента
                            0.30f * s.accent.red, 0.42f * s.accent.red, 0.28f * s.accent.red, 0f, 0f,
                            0.30f * s.accent.green, 0.42f * s.accent.green, 0.28f * s.accent.green, 0f, 0f,
                            0.30f * s.accent.blue, 0.42f * s.accent.blue, 0.28f * s.accent.blue, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            } else null

            Image(
                painter = painterResource(
                    if (s.carGridImage) R.drawable.car_grid else R.drawable.car_rear
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = tint,
                modifier = Modifier.fillMaxSize()
                    .padding(start = if (s.carGridImage) 0.dp else 76.dp)
            )

            // Движение дороги: 30 кадров поверх статичной картинки.
            // Кадр — полоса 610x200 с прозрачностью, поэтому кладём её
            // по низу карточки, а не растягиваем на всю: иначе перспектива
            // кадра разойдётся с перспективой фона. Тот же фильтр цвета,
            // что и у слоя ниже, иначе слои разошлись бы по оттенку.
            if (s.carGridImage) {
                RoadGrid(
                    speedKmh = speedKmh,
                    tint = tint,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.52f)
                )
            }

            // Стоп-полоса поверх перекрашенной картинки.
            // Габариты обязаны оставаться красными в любой теме —
            // иначе единственная узнаваемая деталь машины исчезает.
            if (s.tintCar && s.carGridImage) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.40f)
                        .offset(y = 6.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                0f to Color(0x00FF3B47),
                                0.18f to Color(0xCCFF3B47),
                                0.5f to Color(0xFFFF4B57),
                                0.82f to Color(0xCCFF3B47),
                                1f to Color(0x00FF3B47)
                            )
                        )
                )
            }

            // Затемнение слева, чтобы показания читались поверх фото
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (s.carGridImage) {
                            // На перспективной сетке машина по центру —
                            // затемняем только левый край под цифры
                            Brush.horizontalGradient(
                                0f to s.carCardBg.copy(alpha = 0.85f),
                                0.22f to Color.Transparent
                            )
                        } else {
                            Brush.horizontalGradient(
                                0f to s.carCardBg,
                                0.32f to s.carCardBg.copy(alpha = 0.72f),
                                0.62f to Color.Transparent
                            )
                        }
                    )
            )
        }

        // Приложение занимает карточку целиком, без системной рамки
        if (embeddedPackage != null) {
            EmbeddedAppView(
                packageName = embeddedPackage,
                modifier = Modifier.fillMaxSize(),
                onFailed = onEmbedFailed
            )
            return@Box
        }

        Row(modifier = Modifier.fillMaxSize().padding(dimens().screenPadding + 4.dp)) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (showSpeed) {
                    SpeedWidget(
                        speedKmh = speedKmh,
                        app = speedApp,
                        onClick = onSpeedClick,
                        onLongClick = onSpeedLongClick
                    )
                } else Box(Modifier)

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    RoundToggle(Icons.Rounded.Air, "Климат", onClimate)
                    RoundToggle(Icons.Rounded.Lightbulb, "Свет", onLights)
                }
            }
        }

            // Кнопка выбора приложения для карточки — как «кубик» у CC3.
            // Раньше смена шла только долгим нажатием по спидометру:
            // жест неочевидный, о нём надо знать. Теперь кнопка на виду.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(onClick = onSpeedLongClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LauncherIcons.Cube,
                    contentDescription = "Что показывать в карточке",
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(24.dp)
                )
            }
    }
}

/**
 * Показания скорости в стиле текущей темы.
 *
 * Тап запускает назначенное приложение, удержание — открывает выбор.
 * Если приложение назначено, под цифрами появляется его иконка и название,
 * чтобы было видно, что именно запустится.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedWidget(
    speedKmh: Int,
    app: AppInfo?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val s = LocalThemeSpec.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(s.iconCorner))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(6.dp)
    ) {
        SpeedReadout(speedKmh)

        if (app != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                AppIcon(app.icon, app.label, Modifier.size(20.dp))
                Text(
                    text = app.label,
                    color = s.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = s.fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/** Собственно цифры или кольцо. */
@Composable
private fun SpeedReadout(speedKmh: Int) {
    val s = LocalThemeSpec.current

    when (s.speedStyle) {
        SpeedStyle.AnalogRing -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(dimens().orbSize * 0.9f)) {
                    val stroke = 5.dp.toPx()
                    drawArc(
                        color = s.textDim.copy(alpha = 0.35f),
                        startAngle = 135f, sweepAngle = 270f, useCenter = false,
                        style = Stroke(width = stroke)
                    )
                    val frac = (speedKmh.coerceIn(0, 200) / 200f)
                    drawArc(
                        color = s.accent,
                        startAngle = 135f, sweepAngle = 270f * frac, useCenter = false,
                        style = Stroke(width = stroke)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = speedKmh.toString(),
                        color = s.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = s.fontFamily
                    )
                    Text(
                        text = "km/h",
                        color = s.textSecondary,
                        fontSize = 9.sp,
                        fontFamily = s.fontFamily
                    )
                }
            }
        }
        else -> Column {
            Text(
                text = speedKmh.toString(),
                color = s.textPrimary,
                fontSize = dimens().speedSize,
                fontWeight = if (s.speedStyle == SpeedStyle.DigitalThin) FontWeight.ExtraLight
                             else FontWeight.Light,
                fontFamily = s.fontFamily
            )
            Text(
                text = if (s.uppercaseLabels) "KM/H" else "km/h",
                color = s.textSecondary,
                fontSize = 13.sp,
                fontFamily = s.fontFamily,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun RoundToggle(icon: ImageVector, label: String, onClick: () -> Unit) {
    val s = LocalThemeSpec.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(if (s.cardCorner < 8.dp) RoundedCornerShape(2.dp) else CircleShape)
            .background(s.textPrimary.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = s.textPrimary, modifier = Modifier.size(18.dp))
    }
}
