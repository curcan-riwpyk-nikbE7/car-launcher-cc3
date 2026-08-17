package com.example.carlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Живое превью темы — уменьшенная копия главного экрана.
 *
 * Раньше тема выбиралась по вертикальной полосе с градиентом из двух
 * акцентных цветов. По ней нельзя было понять ничего: Violet и Blue
 * выглядели почти одинаково, потому что у обеих акценты сине-голубые,
 * а отличаются они фоном, карточками и раскладкой.
 *
 * Здесь рисуется настоящая раскладка в цветах темы: панель, медиа,
 * спидометр, приложения, радио. Выбрал тему — сразу видно, что
 * получишь, ещё до применения.
 *
 * Это именно схема, а не работающий экран: настоящие карточки тянут
 * за собой медиа-сессию, GPS и погоду, а превью должно рисоваться
 * мгновенно и без разрешений.
 */
@Composable
fun ThemePreview(spec: ThemeSpec, modifier: Modifier = Modifier) {
    val corner = spec.cardCorner

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(spec.bg))
            .padding(10.dp)
    ) {
        // Раскладка зависит от темы: панель бывает слева, справа или
        // доком снизу. Превью обязано это показывать, иначе оно врёт.
        when (spec.layout) {
            LayoutStyle.BottomDock, LayoutStyle.GridDock -> Column(Modifier.fillMaxSize()) {
                PreviewCards(spec, Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
                PreviewDock(spec)
            }
            LayoutStyle.SidebarRight -> Row(Modifier.fillMaxSize()) {
                PreviewCards(spec, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                PreviewPanel(spec)
            }
            else -> Row(Modifier.fillMaxSize()) {
                PreviewPanel(spec)
                Spacer(Modifier.width(8.dp))
                PreviewCards(spec, Modifier.weight(1f))
            }
        }
    }
}

/** Боковая панель: часы, кольцо ассистента, иконки, кнопка навигации. */
@Composable
private fun PreviewPanel(spec: ThemeSpec) {
    Column(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(spec.panelBg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "13:51",
            color = spec.textPrimary,
            fontSize = 9.sp,
            fontFamily = spec.fontFamily,
            modifier = Modifier.padding(top = 7.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, Brush.linearGradient(listOf(spec.accent, spec.accent2)), CircleShape)
        )
        repeat(2) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(13.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(spec.textDim.copy(alpha = 0.45f))
            )
        }
        Spacer(Modifier.weight(1f))
        // Кнопка навигации — самое узнаваемое пятно панели
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(topStart = 12.dp))
                .background(Brush.linearGradient(listOf(spec.accent, spec.accent2)))
        )
    }
}

/** Горизонтальный док — для тем с нижней раскладкой. */
@Composable
private fun PreviewDock(spec: ThemeSpec) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(spec.panelBg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(spec.textDim.copy(alpha = 0.45f))
            )
        }
    }
}

/** Четыре карточки главного экрана. */
@Composable
private fun PreviewCards(spec: ThemeSpec, modifier: Modifier = Modifier) {
    val corner = RoundedCornerShape(spec.cardCorner / 2)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.weight(1.25f),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Медиа: у неё в темах фирменный градиент, поэтому рисуем им
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(corner)
                    .background(
                        Brush.linearGradient(
                            listOf(spec.accent.copy(alpha = 0.85f), spec.accent2.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }

            // Карточка авто со спидометром
            Box(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
                    .clip(corner)
                    .background(spec.cardBg)
            ) {
                Text(
                    "0",
                    color = spec.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = spec.fontFamily,
                    modifier = Modifier.padding(start = 9.dp, top = 5.dp)
                )
                // Неоновая полоса стопов — узнаваемая деталь карточки
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 13.dp)
                        .fillMaxWidth(0.42f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8452F))
                )
            }
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Приложения
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(corner)
                    .background(spec.cardBg)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .clip(RoundedCornerShape(spec.iconCorner / 2))
                            .background(spec.textDim.copy(alpha = 0.4f))
                    )
                }
            }

            // Радио
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(corner)
                    .background(spec.cardBg),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "87.50",
                    color = spec.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = spec.fontFamily,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}
