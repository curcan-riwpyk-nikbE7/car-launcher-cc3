package com.example.carlauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/**
 * Размеры, которые подстраиваются под экран головного устройства.
 *
 * Жёсткие dp не годятся: у магнитол 1280×720 плотность бывает и 160 dpi
 * (это 1280×720 dp — очень просторно), и 320 dpi (640×360 dp — тесно).
 * Одни и те же 108 dp панели в первом случае выглядят полоской,
 * во втором съедают треть ширины и ломают раскладку.
 *
 * Поэтому всё считается от фактически доступного места.
 */
data class LauncherDimens(
    val panelWidth: Dp,
    val navButtonHeight: Dp,
    val orbSize: Dp,
    val orbRing: Dp,
    val panelIcon: Dp,
    val panelGap: Dp,
    val clockSize: TextUnit,
    val statusBarHeight: Dp,
    val screenPadding: Dp,
    val cardGap: Dp,
    val speedSize: TextUnit,
    val mediaTitle: TextUnit,
    val appIcon: Dp,
    val appLabel: TextUnit,
    val radioWave: Dp,
    val dockHeight: Dp,
    val compact: Boolean
)

/**
 * Считает размеры под конкретный экран.
 *
 * @param w доступная ширина в dp
 * @param h доступная высота в dp
 */
fun calcDimens(w: Dp, h: Dp): LauncherDimens {
    val wv = w.value
    val hv = h.value

    // Тесный экран: мало dp по высоте — типично для плотности 2x на 720p
    val compact = hv < 420f

    // Панель — доля ширины. Пропорции сняты пипеткой со штатного
    // лаунчера CC3 (эталон 1280x720): панель 130 px = 10.2% ширины.
    // Прежние 9.5% с потолком 72 dp давали вдвое узкую панель, из-за
    // чего по кнопкам было трудно попадать на ходу.
    // Нижний предел был 96 dp — на экране с плотностью выше единицы
    // панель упиралась в него и выходила шире эталонных 10.2%.
    // Оставляем только защиту от совсем крайних случаев.
    val panel = (wv * 0.102f).coerceIn(64f, 150f)

    // Орб и иконки считаем от ШИРИНЫ ПАНЕЛИ, а не от высоты экрана.
    // Раньше панель зависела от ширины, а её содержимое — от высоты,
    // и на вытянутом 1280x720 пропорции разъезжались.
    //
    // Важно: сравнивать с эталоном надо ВИДИМЫЙ размер, а не размер бокса.
    // У орба видно только кольцо (68% бокса), у иконки — глиф внутри
    // отступов (54% бокса). Поэтому боксы делаем крупнее: 116 и 81 при
    // панели 131, чтобы на экране получились эталонные 79 и 44.
    val orb = panel * 0.89f
    val icon = panel * 0.68f

    // Нижняя кнопка навигации привязана к высоте: она тянется до края.
    val nav = (hv * 0.176f).coerceIn(72f, 140f)
    val gap = (hv * 0.035f).coerceIn(8f, 26f)

    // Полоса статуса теперь занимает свою высоту над карточками,
    // а не лежит поверх них. Поэтому она должна быть компактной:
    // каждый лишний пиксель забирается у карточек. 5% от 720 = 36 px,
    // значкам 18-19 px этого хватает с запасом.
    val statusH = (hv * 0.050f).coerceIn(24f, 38f)
    val pad = (wv * 0.014f).coerceIn(8f, 18f)
    val cgap = (wv * 0.012f).coerceIn(7f, 15f)

    return LauncherDimens(
        panelWidth = panel.dp,
        navButtonHeight = nav.dp,
        orbSize = orb.dp,
        orbRing = (orb * 0.68f).dp,
        panelIcon = icon.dp,
        panelGap = gap.dp,
        clockSize = (hv * 0.042f).coerceIn(15f, 26f).sp,
        statusBarHeight = statusH.dp,
        screenPadding = pad.dp,
        cardGap = cgap.dp,
        // У CC3 скорость набрана крупно — цифры занимают заметную часть
        // карточки и читаются боковым зрением, не отвлекая от дороги.
        // Прежние 8.5% высоты давали мелковато на 720p.
        speedSize = (hv * 0.125f).coerceIn(34f, 76f).sp,
        mediaTitle = (hv * 0.032f).coerceIn(13f, 20f).sp,
        appIcon = (hv * 0.082f).coerceIn(36f, 56f).dp,
        // Подписи под ярлыками были 9-12 px — на ходу такое не прочитать.
        appLabel = (hv * 0.028f).coerceIn(14f, 18f).sp,
        radioWave = (wv * 0.075f).coerceIn(56f, 92f).dp,
        dockHeight = (hv * 0.075f).coerceIn(38f, 56f).dp,
        compact = compact
    )
}

/** Значения по умолчанию — на случай, если кто-то читает вне BoxWithConstraints. */
val LocalDimens = staticCompositionLocalOf {
    calcDimens(1024.dp, 600.dp)
}

@Composable
fun dimens(): LauncherDimens = LocalDimens.current
