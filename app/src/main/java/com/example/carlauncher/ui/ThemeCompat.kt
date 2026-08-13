package com.example.carlauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Тонкий слой между экранами и активной темой.
 *
 * Раньше цвета были глобальными константами. Теперь они читаются из
 * LocalThemeSpec, поэтому весь интерфейс перекрашивается сам, как только
 * пользователь выбрал другую тему — переписывать каждый экран не пришлось.
 */

val TextPrimary: Color
    @Composable get() = LocalThemeSpec.current.textPrimary

val TextSecondary: Color
    @Composable get() = LocalThemeSpec.current.textSecondary

val TextDim: Color
    @Composable get() = LocalThemeSpec.current.textDim

/** Основной акцент темы (бывший Cyan). */
val Cyan: Color
    @Composable get() = LocalThemeSpec.current.accent

/** Дополнительный акцент (бывший Magenta). */
val Magenta: Color
    @Composable get() = LocalThemeSpec.current.accent2

val Violet: Color
    @Composable get() = LocalThemeSpec.current.accent2

val Pink: Color
    @Composable get() = LocalThemeSpec.current.accent2

val CardBg: Color
    @Composable get() = LocalThemeSpec.current.cardBg

val CardBgSoft: Color
    @Composable get() = LocalThemeSpec.current.cardBg

val CardStroke: Color
    @Composable get() = LocalThemeSpec.current.cardStroke

val SidebarBg: Color
    @Composable get() = LocalThemeSpec.current.panelBg

val CarCardBg: Color
    @Composable get() = LocalThemeSpec.current.carCardBg

val ScreenBackground: Brush
    @Composable get() = LocalThemeSpec.current.bgBrush

val RadioGradient: Brush
    @Composable get() = Brush.verticalGradient(LocalThemeSpec.current.radioGradient)

val NavGradient: Brush
    @Composable get() = LocalThemeSpec.current.let {
        Brush.linearGradient(listOf(it.accent, it.accent2))
    }

/** Базовый градиент медиа-карточки (без углового подмеса). */
val MediaGradient: Brush
    @Composable get() = Brush.verticalGradient(LocalThemeSpec.current.mediaGradient)

/**
 * Второй слой медиа-карточки: акцент затекает из правого нижнего угла.
 * У «плоских» тем подмеса нет — возвращаем полностью прозрачный градиент.
 */
val MediaCornerOverlay: Brush
    @Composable get() {
        val tint = LocalThemeSpec.current.mediaCornerTint
        return if (tint == null) {
            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
        } else {
            Brush.linearGradient(
                colorStops = arrayOf(0.35f to Color.Transparent, 1.0f to tint),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            )
        }
    }

// --- форма ---

val CardCorner: Dp
    @Composable get() = LocalThemeSpec.current.cardCorner

val IconCorner: Dp
    @Composable get() = LocalThemeSpec.current.iconCorner

val ButtonCorner: Dp
    @Composable get() = LocalThemeSpec.current.buttonCorner

val CardStrokeWidth: Dp
    @Composable get() = LocalThemeSpec.current.strokeWidth
