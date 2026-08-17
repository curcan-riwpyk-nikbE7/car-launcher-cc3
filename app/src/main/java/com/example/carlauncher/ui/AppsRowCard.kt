package com.example.carlauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.AppInfo

/** Нижняя карточка «Приложения»: 4 избранных + кнопка Add. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsRowCard(
    favorites: List<AppInfo?>,
    onLaunch: (AppInfo) -> Unit,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    // Делим избранное на страницы по 5 — свайп листает их вбок
    val perPage = 5
    val pages = (favorites.size + perPage - 1) / perPage
    val pagerState = rememberPagerState(pageCount = { pages.coerceAtLeast(1) })

    Box(
        modifier = modifier
            .cardDepth(
                corner = CardCorner,
                accent = LocalThemeSpec.current.accent,
                background = CardBg,
                stroke = CardStroke,
                strokeWidth = CardStrokeWidth
            )
            .padding(horizontal = dimens().screenPadding, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Приложения",
                color = TextSecondary,
                fontSize = 17.sp
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val from = page * perPage
                val slice = favorites.drop(from).take(perPage)
                slice.forEachIndexed { localIndex, app ->
                    val index = from + localIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(IconCorner))
                            .combinedClickable(
                                onClick = { app?.let(onLaunch) ?: onPick(index) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPick(index)
                                }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        // Подложка градиентная, а не плоская заливка:
                        // на референсе под иконками мягкое свечение,
                        // из-за которого они не выглядят наклеенными
                        // на карточку.
                        Box(
                            modifier = Modifier
                                .size(dimens().appIcon)
                                .clip(RoundedCornerShape(IconCorner))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.10f),
                                            Color.White.copy(alpha = 0.03f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (app != null) {
                                AppIcon(app.icon, app.label, Modifier.size(dimens().appIcon * 0.68f))
                            } else {
                                Icon(
                                    Icons.Rounded.Add, "Добавить",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Text(
                            text = app?.label ?: "Add",
                            color = if (app != null) TextPrimary else TextDim,
                            fontSize = dimens().appLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            }

            // Точки-индикаторы страниц
            if (pages > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 2.dp)
                ) {
                    repeat(pages) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == pagerState.currentPage) 7.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pagerState.currentPage) Cyan
                                    else TextDim.copy(alpha = 0.5f)
                                )
                        )
                    }
                }
            }
        }
    }
}
