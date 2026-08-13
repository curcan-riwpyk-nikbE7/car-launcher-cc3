package com.example.carlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Галерея тем. Каждая карточка — уменьшенный макет главного экрана,
 * нарисованный в цветах и форме своей темы, поэтому сразу видно,
 * что меняется не только палитра, но и раскладка.
 */
@Composable
fun ThemePickerScreen(
    currentId: String,
    onPick: (String) -> Unit
) {
    val spec = LocalThemeSpec.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(spec.bgBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            Text(
                text = themedLabel("Оформление"),
                color = spec.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = spec.fontFamily
            )
            Text(
                text = "Меняется палитра, форма карточек и раскладка панели",
                color = spec.textSecondary,
                fontSize = 13.sp,
                fontFamily = spec.fontFamily,
                modifier = Modifier.padding(top = 3.dp, bottom = 14.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(AllThemes, key = { it.id }) { t ->
                    ThemeCard(
                        spec = t,
                        selected = t.id == currentId,
                        onClick = { onPick(t.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(spec: ThemeSpec, selected: Boolean, onClick: () -> Unit) {
    val active = LocalThemeSpec.current

    Column(
        modifier = Modifier
            .width(268.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(active.cardCorner))
            .background(active.cardBg)
            .border(
                width = if (selected) 2.dp else active.strokeWidth,
                color = if (selected) active.accent else active.cardStroke,
                shape = RoundedCornerShape(active.cardCorner)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        // Живой мини-макет в цветах своей темы
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(spec.cardCorner))
                .background(spec.bgBrush)
                .padding(7.dp)
        ) {
            ThemeMiniature(spec)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spec.title,
                    color = active.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = active.fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = spec.subtitle,
                    color = active.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = active.fontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(active.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Check, "Выбрана",
                        tint = active.onAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** Схематичный главный экран темы: панель, медиа, авто, две нижние карточки. */
@Composable
private fun ThemeMiniature(s: ThemeSpec) {
    val panel = @Composable { mod: Modifier ->
        Box(
            modifier = mod
                .clip(RoundedCornerShape(s.cardCorner / 2))
                .background(s.panelBg)
        ) {
            // точки-иконки панели
            val horizontal = s.layout == LayoutStyle.BottomDock || s.layout == LayoutStyle.TopBar
            if (horizontal) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) { repeat(4) { Dot(s.accent, s) } }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { repeat(4) { Dot(s.accent, s) } }
            }
        }
    }

    val content = @Composable { mod: Modifier ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1.1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // медиа-карточка с градиентом темы
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(s.cardCorner / 2))
                        .background(Brush.verticalGradient(s.mediaGradient))
                )
                // карточка авто
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(s.cardCorner / 2))
                        .background(s.carCardBg),
                    contentAlignment = Alignment.CenterStart
                ) {
                    SpeedGlyph(s)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().weight(0.9f),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(s.cardCorner / 2))
                        .background(s.cardBg),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(RoundedCornerShape(s.iconCorner / 2))
                                    .background(s.accent.copy(alpha = 0.75f))
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(s.cardCorner / 2))
                        .background(s.cardBg),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .padding(5.dp)
                            .width(26.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(s.cardCorner / 2))
                            .background(Brush.verticalGradient(s.radioGradient))
                    )
                }
            }
        }
    }

    when (s.layout) {
        LayoutStyle.SidebarLeft -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            panel(Modifier.width(20.dp).fillMaxHeight())
            content(Modifier.weight(1f).fillMaxHeight())
        }
        LayoutStyle.SidebarRight -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            content(Modifier.weight(1f).fillMaxHeight())
            panel(Modifier.width(20.dp).fillMaxHeight())
        }
        LayoutStyle.BottomDock, LayoutStyle.GridDock -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            content(Modifier.fillMaxWidth().weight(1f))
            panel(Modifier.fillMaxWidth().height(20.dp))
        }
        LayoutStyle.TopBar -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            panel(Modifier.fillMaxWidth().height(20.dp))
            content(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun Dot(color: Color, s: ThemeSpec) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(if (s.cardCorner < 8.dp) RoundedCornerShape(1.dp) else CircleShape)
            .background(color.copy(alpha = 0.8f))
    )
}

/** Значок скорости: цифры или кольцо — по стилю темы. */
@Composable
private fun SpeedGlyph(s: ThemeSpec) {
    when (s.speedStyle) {
        SpeedStyle.AnalogRing -> Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(24.dp)
                .clip(CircleShape)
                .border(2.dp, s.accent, CircleShape)
        )
        else -> Text(
            text = "0",
            color = s.textPrimary,
            fontSize = if (s.speedStyle == SpeedStyle.DigitalThin) 16.sp else 20.sp,
            fontWeight = if (s.speedStyle == SpeedStyle.DigitalThin) FontWeight.Light else FontWeight.Normal,
            fontFamily = s.fontFamily,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
