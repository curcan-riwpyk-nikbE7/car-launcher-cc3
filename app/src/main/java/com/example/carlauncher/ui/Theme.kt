package com.example.carlauncher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Где расположена панель управления. Это меняет всю раскладку экрана. */
enum class LayoutStyle { SidebarLeft, SidebarRight, BottomDock, TopBar, GridDock }

/** Как рисуется скорость на карточке авто. */
enum class SpeedStyle { DigitalLarge, DigitalThin, AnalogRing }

/** Как выглядят часы на панели. */
enum class ClockStyle { DigitalLarge, DigitalCompact, Analog, HeroRight }

/**
 * Полное описание темы: цвета, форма и раскладка.
 * Темы отличаются не только палитрой — у них разное расположение панели,
 * радиус скруглений, стиль спидометра и часов.
 */
data class ThemeSpec(
    val id: String,
    val title: String,
    val subtitle: String,

    // --- цвета ---
    val bg: List<Color>,
    val cardBg: Color,
    val cardStroke: Color,
    val strokeWidth: Dp,
    val panelBg: Color,
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDim: Color,
    val carCardBg: Color,
    val mediaGradient: List<Color>,
    /** Пурпур/акцент, затекающий из угла медиа-карточки. null — без подмеса. */
    val mediaCornerTint: Color?,
    val radioGradient: List<Color>,
    val overlayBg: Color,

    // --- форма ---
    val cardCorner: Dp,
    val iconCorner: Dp,
    val buttonCorner: Dp,

    // --- раскладка и виджеты ---
    val layout: LayoutStyle,
    val speedStyle: SpeedStyle,
    val clockStyle: ClockStyle,
    val showCarImage: Boolean,
    /** Медиа-виджет рисуется как смартфон с неоновым свечением. */
    val phoneMedia: Boolean = false,
    /** Ресурс картинки авто: сетка-перспектива или обычное фото. */
    val carGridImage: Boolean = false,
    /**
     * Перекрашивать картинку машины под акцент темы.
     * У Violet выключено: исходник и так фиолетовый, фильтр только
     * замутил бы его. Остальным темам без перекраски машина
     * оставалась бы чужеродно-фиолетовой.
     */
    val tintCar: Boolean = false,
    val showDecorRings: Boolean,
    val uppercaseLabels: Boolean,
    val monospace: Boolean,
    val isLight: Boolean
) {
    val bgBrush: Brush get() = Brush.linearGradient(bg)
    val fontFamily: FontFamily get() = if (monospace) FontFamily.Monospace else FontFamily.Default
}

// Четыре темы штатного лаунчера CC3: Violet, Blue, Gold, Black.
// Свои семь убраны намеренно — задача была повторить оригинал, а не
// расширять. Палитра снята пипеткой со скриншотов и промо-галереи.

// ────────────────────────────── 1. VIOLET ──────────────────────────────
private val VioletTheme = ThemeSpec(
    id = "violet",
    title = "Violet",
    subtitle = "Фиолетовый · как на CC3 по умолчанию",
    bg = listOf(
        Color(0xFF141126),
        Color(0xFF1B1740),
        Color(0xFF2E1F5E),
        Color(0xFF221847)
    ),
    cardBg = Color(0xFF1D2242),
    cardStroke = Color(0x338E9CFF),
    strokeWidth = 1.dp,
    panelBg = Color(0xFF14193F),
    accent = Color(0xFF22D3EE),
    accent2 = Color(0xFFE255F5),
    onAccent = Color(0xFF0A0B1E),
    textPrimary = Color(0xFFF0F3FF),
    textSecondary = Color(0xFF9BA6DC),
    textDim = Color(0xFF6B76B0),
    carCardBg = Color(0xFF241C5E),
    mediaGradient = listOf(Color(0xFF1E5BE0), Color(0xFF2A7BF0), Color(0xFF7C4DF0)),
    mediaCornerTint = Color(0x66E255F5),
    radioGradient = listOf(Color(0xFF2F3480), Color(0xFF242554)),
    overlayBg = Color(0xEE121639),
    cardCorner = 24.dp,
    iconCorner = 18.dp,
    buttonCorner = 20.dp,
    layout = LayoutStyle.SidebarLeft,
    speedStyle = SpeedStyle.DigitalLarge,
    clockStyle = ClockStyle.DigitalLarge,
    showCarImage = true,
    phoneMedia = true,
    carGridImage = true,
    showDecorRings = false,
    uppercaseLabels = false,
    monospace = false,
    isLight = false
)

// ────────────────────────────── 2. BLUE ──────────────────────────────
private val BlueTheme = ThemeSpec(
    id = "blue",
    title = "Blue",
    subtitle = "Сине-бирюзовый · холодный",
    bg = listOf(
        Color(0xFF0A1420),
        Color(0xFF0E2136),
        Color(0xFF10344F),
        Color(0xFF0C2438)
    ),
    cardBg = Color(0xFF13263C),
    cardStroke = Color(0x3352C4E8),
    strokeWidth = 1.dp,
    panelBg = Color(0xFF0D1E31),
    accent = Color(0xFF2BE0D0),
    accent2 = Color(0xFF3FA9F5),
    onAccent = Color(0xFF04121A),
    textPrimary = Color(0xFFEAF6FF),
    textSecondary = Color(0xFF8FB4CC),
    textDim = Color(0xFF5C7E96),
    carCardBg = Color(0xFF102C44),
    mediaGradient = listOf(Color(0xFF0E7490), Color(0xFF1FA3C4), Color(0xFF2BE0D0)),
    mediaCornerTint = Color(0x662BE0D0),
    radioGradient = listOf(Color(0xFF13415C), Color(0xFF102A3E)),
    overlayBg = Color(0xEE0A1826),
    cardCorner = 24.dp,
    iconCorner = 18.dp,
    buttonCorner = 20.dp,
    layout = LayoutStyle.SidebarLeft,
    speedStyle = SpeedStyle.DigitalLarge,
    clockStyle = ClockStyle.DigitalLarge,
    showCarImage = true,
    phoneMedia = true,
    carGridImage = true,
    tintCar = true,
    showDecorRings = false,
    uppercaseLabels = false,
    monospace = false,
    isLight = false
)

// ────────────────────────────── 3. GOLD ──────────────────────────────
private val GoldTheme = ThemeSpec(
    id = "gold",
    title = "Gold",
    subtitle = "Песочно-золотой · тёплый",
    bg = listOf(
        Color(0xFF17130C),
        Color(0xFF221B10),
        Color(0xFF332714),
        Color(0xFF241C11)
    ),
    cardBg = Color(0xFF241D12),
    cardStroke = Color(0x33E8C88A),
    strokeWidth = 1.dp,
    panelBg = Color(0xFF1B160E),
    accent = Color(0xFFE8C88A),
    accent2 = Color(0xFFC9A227),
    onAccent = Color(0xFF1A1408),
    textPrimary = Color(0xFFFBF3E2),
    textSecondary = Color(0xFFC4B08A),
    textDim = Color(0xFF8A7A5C),
    carCardBg = Color(0xFF2A2113),
    mediaGradient = listOf(Color(0xFF8A6A1F), Color(0xFFC9A227), Color(0xFFE8C88A)),
    mediaCornerTint = Color(0x66E8C88A),
    radioGradient = listOf(Color(0xFF3A2E18), Color(0xFF262010)),
    overlayBg = Color(0xEE17130C),
    cardCorner = 24.dp,
    iconCorner = 18.dp,
    buttonCorner = 20.dp,
    layout = LayoutStyle.SidebarLeft,
    speedStyle = SpeedStyle.DigitalLarge,
    clockStyle = ClockStyle.DigitalLarge,
    showCarImage = true,
    phoneMedia = true,
    carGridImage = true,
    tintCar = true,
    showDecorRings = false,
    uppercaseLabels = false,
    monospace = false,
    isLight = false
)

// ────────────────────────────── 4. BLACK ──────────────────────────────
private val BlackTheme = ThemeSpec(
    id = "black",
    title = "Black",
    subtitle = "Графитовый · минимум цвета, меньше слепит ночью",
    bg = listOf(
        Color(0xFF0A0A0C),
        Color(0xFF121216),
        Color(0xFF1A1A20),
        Color(0xFF101014)
    ),
    cardBg = Color(0xFF16161B),
    cardStroke = Color(0x33FFFFFF),
    strokeWidth = 1.dp,
    panelBg = Color(0xFF101013),
    accent = Color(0xFFDCDCE4),
    accent2 = Color(0xFF8A8A99),
    onAccent = Color(0xFF0A0A0C),
    textPrimary = Color(0xFFF2F2F5),
    textSecondary = Color(0xFF9E9EAC),
    textDim = Color(0xFF64646F),
    carCardBg = Color(0xFF141419),
    mediaGradient = listOf(Color(0xFF2A2A32), Color(0xFF3C3C46), Color(0xFF52525E)),
    mediaCornerTint = null,
    radioGradient = listOf(Color(0xFF26262E), Color(0xFF18181D)),
    overlayBg = Color(0xEE0C0C0F),
    cardCorner = 24.dp,
    iconCorner = 18.dp,
    buttonCorner = 20.dp,
    layout = LayoutStyle.SidebarLeft,
    speedStyle = SpeedStyle.DigitalLarge,
    clockStyle = ClockStyle.DigitalLarge,
    showCarImage = true,
    phoneMedia = true,
    carGridImage = true,
    tintCar = true,
    showDecorRings = false,
    uppercaseLabels = false,
    monospace = false,
    isLight = false
)

/** Все темы в порядке показа в галерее. */
val AllThemes = listOf(VioletTheme, BlueTheme, GoldTheme, BlackTheme)

fun themeById(id: String?): ThemeSpec =
    AllThemes.firstOrNull { it.id == id } ?: VioletTheme

val LocalThemeSpec = staticCompositionLocalOf { VioletTheme }

@Composable
fun CarLauncherTheme(spec: ThemeSpec = VioletTheme, content: @Composable () -> Unit) {
    val scheme = if (spec.isLight) {
        lightColorScheme(
            primary = spec.accent,
            onPrimary = spec.onAccent,
            background = spec.bg.first(),
            onBackground = spec.textPrimary,
            surface = spec.cardBg,
            onSurface = spec.textPrimary,
            surfaceVariant = spec.cardBg,
            onSurfaceVariant = spec.textSecondary
        )
    } else {
        darkColorScheme(
            primary = spec.accent,
            onPrimary = spec.onAccent,
            background = spec.bg.first(),
            onBackground = spec.textPrimary,
            surface = spec.cardBg,
            onSurface = spec.textPrimary,
            surfaceVariant = spec.cardBg,
            onSurfaceVariant = spec.textSecondary
        )
    }

    val f = spec.fontFamily
    val typography = Typography(
        displayLarge = TextStyle(fontFamily = f, fontSize = 62.sp, fontWeight = FontWeight.Light),
        titleLarge = TextStyle(fontFamily = f, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = f, fontSize = 15.sp),
        labelLarge = TextStyle(fontFamily = f, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = f, fontSize = 11.sp)
    )

    CompositionLocalProvider(LocalThemeSpec provides spec) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** Приводит подпись к стилю темы (Cyber и Retro — заглавными). */
@Composable
fun themedLabel(text: String): String =
    if (LocalThemeSpec.current.uppercaseLabels) text.uppercase() else text
