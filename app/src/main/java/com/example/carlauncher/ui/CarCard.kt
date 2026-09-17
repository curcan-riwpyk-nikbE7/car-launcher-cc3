package com.example.carlauncher.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.example.carlauncher.data.SettingsStore
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Красный «превышения» — тот же тон, что у стоп-полосы на картинке
 * машины (0xFFFF4B57). Темам его не доверяем: у светлых тем акцент
 * может оказаться зелёным, а тревога обязана оставаться красной.
 */
private val OverLimitRed = Color(0xFFFF4B57)

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
    /** Режим содержимого карточки: speed, map, widget, youtube. */
    contentMode: String = SettingsStore.CARD_MODE_SPEED,
    widgetId: Int = SettingsStore.cardWidgetId.value,
    onPickWidget: () -> Unit = {},
    /** Пакет приложения, встроенного прямо в карточку (null — спидометр). */
    embeddedPackage: String? = null,
    onEmbedFailed: () -> Unit = {},
    /** Тап по кнопке «спидометр» под встроенным приложением — выйти из карты. */
    onBackToSpeed: () -> Unit = {},
    /** Кнопка «на весь экран» под встроенным приложением. */
    onOpenFullscreen: () -> Unit = {},
    onClimate: () -> Unit,
    onLights: () -> Unit,
    onExpand: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    val cardHaptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .cardDepth(
                corner = s.cardCorner,
                accent = s.accent,
                background = s.carCardBg,
                stroke = s.cardStroke,
                strokeWidth = s.strokeWidth
            )
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

            // Зарево над линией горизонта. На картинке дорога уходит
            // в темноту резким срезом; мягкий свет над горизонтом
            // прячет этот стык и добавляет глубины — так же сделано
            // на референсных снимках CC3.
            if (s.carGridImage) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .fillMaxHeight(0.34f)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    s.accent2.copy(alpha = 0.10f),
                                    s.accent.copy(alpha = 0.16f),
                                    Color.Transparent
                                )
                            )
                        )
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

        if (contentMode == SettingsStore.CARD_MODE_MAP) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(s.cardCorner))
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    EmbeddedMapView(
                        speedKmh = speedKmh,
                        onOpenFullNavi = onOpenFullscreen,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                EmbedCardBar(
                    title = "Живая карта (GPS)",
                    onBackToSpeed = onBackToSpeed,
                    onFullscreen = onOpenFullscreen,
                    onPickApp = onSpeedLongClick
                )
            }
            return@Box
        }

        if (contentMode == SettingsStore.CARD_MODE_WIDGET) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(s.cardCorner))
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AppWidgetCardView(
                        widgetId = widgetId,
                        onPickWidget = onPickWidget,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                EmbedCardBar(
                    title = "Виджет",
                    onBackToSpeed = onBackToSpeed,
                    onFullscreen = onOpenFullscreen,
                    onPickApp = onSpeedLongClick
                )
            }
            return@Box
        }

        if (contentMode == SettingsStore.CARD_MODE_YOUTUBE) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(s.cardCorner))
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    EmbeddedYouTubeView(
                        onOpenFullscreen = onOpenFullscreen,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                EmbedCardBar(
                    title = "YouTube",
                    onBackToSpeed = onBackToSpeed,
                    onFullscreen = onOpenFullscreen,
                    onPickApp = onSpeedLongClick
                )
            }
            return@Box
        }

        // Приложение занимает карточку целиком, без системной рамки.
        // Под ним узкая строка управления: название приложения и кнопки
        // «вернуть спидометр» (тап — карта, ещё тап — спидометр, как
        // в штатных лаунчерах) и «сменить приложение». Строка лежит
        // ПОД поверхностью приложения, а не поверх неё: Compose не
        // умеет рисовать поверх SurfaceView в той же иерархии.
        if (embeddedPackage != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(s.cardCorner))
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    EmbeddedAppView(
                        packageName = embeddedPackage,
                        modifier = Modifier.fillMaxSize(),
                        onFailed = onEmbedFailed
                    )
                    // Быстрые маршруты ПОВЕРХ карты, как у Reglink/CC3.
                    // Показываются только для навигаторов; Compose рисует
                    // поверх SurfaceView (тот живёт в отдельном Surface
                    // под окном лаунчера), поэтому кнопки видны и нажимаемы.
                    NaviQuickOverlay(
                        modifier = Modifier.align(Alignment.TopStart),
                        navigatorPkg = embeddedPackage
                    )
                }
                EmbedCardBar(
                    app = speedApp,
                    onBackToSpeed = onBackToSpeed,
                    onFullscreen = onOpenFullscreen,
                    onPickApp = onSpeedLongClick
                )
            }
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

/** Собственно цифры, кольцо или стрелочный циферблат. */
@Composable
private fun SpeedReadout(speedKmh: Int) {
    val s = LocalThemeSpec.current

    // Превышение заданного лимита: цифры краснеют, чтобы водитель видел
    // выход за порог боковым зрением. Звук добавляется отдельно (см.
    // HomeScreen), здесь — только цвет.
    val overLimit = SettingsStore.speedLimitEnabled.value &&
        speedKmh >= SettingsStore.speedLimitKmh.value
    val valueColor = if (overLimit) OverLimitRed else s.textPrimary
    val unitColor = if (overLimit) OverLimitRed else s.textSecondary

    when (speedStyleFor(s)) {
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
                        color = if (overLimit) OverLimitRed else s.accent,
                        startAngle = 135f, sweepAngle = 270f * frac, useCenter = false,
                        style = Stroke(width = stroke)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = speedKmh.toString(),
                        color = valueColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = s.fontFamily
                    )
                    Text(
                        text = "km/h",
                        color = unitColor,
                        fontSize = 9.sp,
                        fontFamily = s.fontFamily
                    )
                }
            }
        }
        SpeedStyle.AnalogGauge -> GaugeSpeedo(speedKmh = speedKmh)
        else -> Column {
            Text(
                text = speedKmh.toString(),
                color = valueColor,
                fontSize = dimens().speedSize,
                fontWeight = if (speedStyleFor(s) == SpeedStyle.DigitalThin) FontWeight.ExtraLight
                             else FontWeight.Light,
                fontFamily = s.fontFamily
            )
            Text(
                text = if (s.uppercaseLabels) "KM/H" else "km/h",
                color = unitColor,
                fontSize = 13.sp,
                fontFamily = s.fontFamily,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * Вид спидометра с учётом настройки «Экран → Показатели → Вид спидометра».
 * Пустая строка — стиль из темы (сейчас все темы — крупные цифры).
 * Единая точка правды для карточки авто и развёрнутой карточки.
 */
internal fun speedStyleFor(theme: ThemeSpec): SpeedStyle =
    when (SettingsStore.speedStyleOverride.value) {
        "thin" -> SpeedStyle.DigitalThin
        "ring" -> SpeedStyle.AnalogRing
        "gauge" -> SpeedStyle.AnalogGauge
        else -> theme.speedStyle
    }

/**
 * Стрелочный спидометр: подкова шкалы с рисками, плавно плывущая стрелка
 * и крупная цифра на тёмном «щитке» в центре — как в современных машинах.
 *
 * Скорость с GPS приходит раз в секунду и меняется скачками; стрелка и
 * цифра ведутся от одной анимированной величины, поэтому плывут, а не
 * дёргаются, и никогда не разъезжаются друг с другом. Пока скорость не
 * меняется, анимация не работает и кадры не тратятся.
 *
 * @param mult множитель размера: 1 — на карточке авто, больше — на
 *   развёрнутой карточке (полноэкранная «приборка»).
 */
@Composable
internal fun GaugeSpeedo(speedKmh: Int, mult: Float = 1f) {
    val s = LocalThemeSpec.current
    val maxKmh = 200

    val animated by animateFloatAsState(
        targetValue = speedKmh.coerceIn(0, maxKmh).toFloat(),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "speedGauge"
    )

    // Превышение лимита — тревога обязана оставаться красной (см. OverLimitRed).
    val overLimit = SettingsStore.speedLimitEnabled.value &&
        speedKmh >= SettingsStore.speedLimitKmh.value
    val valueColor = if (overLimit) OverLimitRed else s.textPrimary
    val unitColor = if (overLimit) OverLimitRed else s.textSecondary
    val danger = if (overLimit) OverLimitRed else s.accent

    // Циферблат крупнее кольца: стрелке нужен размах, а цифре — место.
    // Размер считаем от панели (как орб), чтобы на тесных экранах
    // шкала не упиралась в края карточки.
    val gauge = dimens().orbSize * 1.3f * mult
    // Цифра в центре — доля циферблата: на больших экранах она крупная,
    // на тесных не вылезает за щиток даже тремя знаками («200»).
    val valueSize = (dimens().orbSize.value * 0.20f * mult).sp
    val unitSize = (9f * mult).sp

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(gauge)) {
            val d = size.minDimension
            val c = center
            val stroke = 5.dp.toPx()
            // Внешний край штрихов шкалы отстоит от края канваса.
            val pad = 8.dp.toPx()
            // Радиус центра трека: штрихи снаружи, дуга под ними.
            val trackR = d / 2f - pad - stroke / 2f - 2.dp.toPx()

            // Трек-подкова: от «7 часов» до «5 часов», низ открыт.
            val trackColor = s.textDim.copy(alpha = 0.35f)
            drawArc(
                color = trackColor,
                startAngle = 135f, sweepAngle = 270f, useCenter = false,
                style = Stroke(width = stroke)
            )
            // Пройденная часть шкалы — акцентом (красным при превышении).
            val frac = animated / maxKmh
            if (frac > 0f) {
                drawArc(
                    color = danger,
                    startAngle = 135f, sweepAngle = 270f * frac, useCenter = false,
                    style = Stroke(width = stroke)
                )
            }

            // Риски: каждые 10 км/ч — короткие, каждые 20 — длинные.
            // Начинаются у внешнего края и идут внутрь, к треку.
            val rOut = trackR + stroke / 2f + 2.dp.toPx()
            val rad = kotlin.math.PI.toFloat() / 180f
            for (v in 0..maxKmh step 10) {
                val a = (135f + 270f * (v / maxKmh.toFloat())) * rad
                val major = v % 20 == 0
                val len = if (major) 13.dp.toPx() else 7.dp.toPx()
                val x1 = c.x + cos(a) * rOut
                val y1 = c.y + sin(a) * rOut
                val x2 = c.x + cos(a) * (rOut - len)
                val y2 = c.y + sin(a) * (rOut - len)
                drawLine(
                    color = if (major) s.textSecondary.copy(alpha = 0.85f)
                            else s.textSecondary.copy(alpha = 0.45f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = if (major) 2.5.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Стрелка от центра к шкале. Её «корень» спрячется под щитком,
            // который рисуется следом, — стрелка выглядит растущей из-под
            // цифры, как на современных приборках.
            val needleA = (135f + 270f * frac) * rad
            val tipR = trackR - 15.dp.toPx()
            drawLine(
                color = danger,
                start = c,
                end = Offset(c.x + cos(needleA) * tipR, c.y + sin(needleA) * tipR),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // «Щиток» под цифру: скрывает корень стрелки и даёт цифре
            // спокойный тёмный фон поверх фото машины.
            val diskR = trackR * 0.42f
            drawCircle(color = s.carCardBg, radius = diskR, center = c)
            drawCircle(
                color = s.textDim.copy(alpha = 0.30f),
                radius = diskR,
                center = c,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        // Цифра и единицы — настоящим текстом поверх щитка (не в Canvas):
        // так размер подстраивается сам и текст остаётся доступным.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = animated.roundToInt().toString(),
                color = valueColor,
                fontSize = valueSize,
                fontWeight = FontWeight.Medium,
                fontFamily = s.fontFamily
            )
            Text(
                text = if (s.uppercaseLabels) "KM/H" else "km/h",
                color = unitColor,
                fontSize = unitSize,
                fontFamily = s.fontFamily
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

/**
 * Нижняя строка карточки, когда в неё встроено приложение.
 *
 * Слева — название встроенного приложения, справа кнопки: «вернуть
 * спидометр» (карта сворачивается обратно в спидометр) и «сменить
 * приложение». Строка идёт отдельным рядом ПОД поверхностью приложения,
 * поэтому её кнопки всегда видны и нажимаются — поверх SurfaceView
 * Compose рисовать не умеет.
 */
@Composable
private fun EmbedCardBar(
    app: AppInfo? = null,
    title: String = app?.label ?: "",
    onBackToSpeed: () -> Unit,
    onFullscreen: () -> Unit,
    onPickApp: () -> Unit
) {
    val s = LocalThemeSpec.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(s.carCardBg.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (app != null) {
            AppIcon(app.icon, app.label, Modifier.size(18.dp))
        }
        Text(
            text = title,
            color = s.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = s.fontFamily,
            modifier = Modifier.weight(1f)
        )
        RoundToggle(Icons.Rounded.Speed, "Вернуть спидометр", onBackToSpeed)
        RoundToggle(Icons.Rounded.OpenInFull, "На весь экран", onFullscreen)
        RoundToggle(LauncherIcons.Cube, "Сменить режим", onPickApp)
    }
}
