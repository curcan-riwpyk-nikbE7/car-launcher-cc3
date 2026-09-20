package com.example.carlauncher.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.AppInfo
import com.example.carlauncher.data.AppRepository
import com.example.carlauncher.data.TaskMover
import com.example.carlauncher.data.NowPlaying
import com.example.carlauncher.data.SettingsStore

/**
 * Флагманская 3-панельная раскладка TEYES.PRO (CC3 / CC3 2K):
 * - Левая колонка: сетка приложений 2×3, мини-кнопки быстрого доступа и карточка маршрута.
 * - Центральная колонка: карта (Google Roads или встроенный Яндекс Навигатор) на всю высоту.
 * - Правая колонка: большой медиаплеер с крупной обложкой альбома и автомобильными кнопками.
 */
@Composable
fun TeyesTriPanel(
    apps: List<AppInfo>,
    favorites: List<AppInfo?>,
    nowPlaying: NowPlaying,
    speedKmh: Int,
    speedApp: AppInfo?,
    onSpeedClick: () -> Unit,
    onSpeedLongClick: () -> Unit,
    onPickSlot: (Int) -> Unit,
    onPickWidget: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenPlayer: () -> Unit,
    onBounds: (android.graphics.Rect) -> Unit,
    onEmbedFailed: () -> Unit,
    onBackToSpeed: () -> Unit,
    navApp: AppInfo? = null,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 1. ЛЕВАЯ КОЛОНКА: Сетка приложений + Быстрые слоты + Навигация ──
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Сетка 2×3 основных приложений
            LeftAppGridCard(
                favorites = favorites,
                onLaunch = { AppRepository.launch(context, it) },
                onPickSlot = onPickSlot,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.85f)
            )

            // Информационная карточка маршрута навигатора ("Расстояние: 0 км 22 мин.")
            RouteDistanceCard(
                onClick = {
                    AppRepository.launchFirstAvailable(context, AppRepository.NAVIGATION)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            )
        }

        // ── 2. ЦЕНТРАЛЬНАЯ КОЛОНКА: Карта / Навигатор от края до края ──
        Box(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
        ) {
            CarCard(
                speedKmh = speedKmh,
                showSpeed = SettingsStore.showSpeed.value,
                speedApp = speedApp,
                onSpeedClick = onSpeedClick,
                onSpeedLongClick = onSpeedLongClick,
                onBounds = onBounds,
                contentMode = SettingsStore.cardContentMode.value,
                widgetId = SettingsStore.cardWidgetId.value,
                onPickWidget = onPickWidget,
                embeddedPackage = navApp?.packageName,
                onEmbedFailed = onEmbedFailed,
                onBackToSpeed = onBackToSpeed,
                onOpenFullscreen = {
                    when (SettingsStore.cardContentMode.value) {
                        SettingsStore.CARD_MODE_MAP, SettingsStore.CARD_MODE_EMBEDDED -> {
                            val navPkg = if (speedApp != null && AppRepository.NAVIGATION.contains(speedApp.packageName)) {
                                speedApp.packageName
                            } else {
                                AppRepository.findFirstInstalled(context, AppRepository.NAVIGATION)
                                    ?: speedApp?.packageName
                                    ?: "ru.yandex.yandexmaps"
                            }
                            if (!TaskMover.moveToMainDisplay(context, navPkg)) {
                                AppRepository.launchPackage(context, navPkg)
                            }
                        }
                        SettingsStore.CARD_MODE_YOUTUBE -> {
                            val ytCandidates = listOf(
                                "com.google.android.youtube",
                                "app.revanced.android.youtube",
                                "com.vanced.android.youtube",
                                "com.google.android.apps.youtube.mango",
                                "org.videolan.vlc",
                                "com.mxtech.videoplayer.ad"
                            )
                            val ytPkg = AppRepository.findFirstInstalled(context, ytCandidates, AppRepository.VIDEO_LABELS)
                                ?: "com.google.android.youtube"
                            if (!TaskMover.moveToMainDisplay(context, ytPkg)) {
                                AppRepository.launchFirstAvailable(context, ytCandidates, labels = AppRepository.VIDEO_LABELS)
                            }
                        }
                        else -> {
                            val a = speedApp
                            if (a != null) {
                                AppRepository.launch(context, a)
                            } else {
                                AppRepository.launchFirstAvailable(context, AppRepository.NAVIGATION)
                            }
                        }
                    }
                },
                onClimate = {
                    AppRepository.launchFirstAvailable(
                        context, AppRepository.CLIMATE,
                        errorText = "Климат-контроль недоступен на этом ГУ"
                    )
                },
                onLights = {
                    AppRepository.launchFirstAvailable(
                        context, AppRepository.CAR_INFO,
                        errorText = "Приложение автомобиля не найдено"
                    )
                },
                onExpand = {},
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── 3. ПРАВАЯ КОЛОНКА: Флагманский медиаплеер TEYES ──
        TeyesMediaCard(
            state = nowPlaying,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrev = onPrev,
            onOpenPlayer = onOpenPlayer,
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight()
        )
    }
}

/**
 * Сетка приложений 2×3 в левой колонке:
 * 6 крупных ячеек с иконками и подписями.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LeftAppGridCard(
    favorites: List<AppInfo?>,
    onLaunch: (AppInfo) -> Unit,
    onPickSlot: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(s.cardBg)
            .border(s.strokeWidth, s.cardStroke, RoundedCornerShape(s.cardCorner))
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // Верхний ряд (3 иконки)
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 3) {
                AppGridItem(
                    app = favorites.getOrNull(i),
                    slotIndex = i,
                    onLaunch = onLaunch,
                    onPickSlot = onPickSlot,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Нижний ряд (3 иконки)
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 3 until 6) {
                AppGridItem(
                    app = favorites.getOrNull(i),
                    slotIndex = i,
                    onLaunch = onLaunch,
                    onPickSlot = onPickSlot,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(
    app: AppInfo?,
    slotIndex: Int,
    onLaunch: (AppInfo) -> Unit,
    onPickSlot: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = {
                    if (app != null) onLaunch(app) else onPickSlot(slotIndex)
                },
                onLongClick = { onPickSlot(slotIndex) }
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (app != null) {
            when (app.packageName) {
                "builtin:all_apps" -> {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(s.accent.copy(alpha = 0.18f))
                            .border(1.dp, s.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Apps,
                            contentDescription = app.label,
                            tint = s.accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                "builtin:settings" -> {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(s.accent.copy(alpha = 0.18f))
                            .border(1.dp, s.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = app.label,
                            tint = s.accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                else -> {
                    AppIcon(
                        drawable = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = app.label,
                color = s.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = s.fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        } else {
            // Пустой слот с кнопкой "+"
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, s.accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Добавить",
                    tint = s.accent.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Добавить",
                color = s.textSecondary,
                fontSize = 10.sp,
                fontFamily = s.fontFamily,
                maxLines = 1
            )
        }
    }
}

/**
 * Карточка статуса активного маршрута:
 * Зеленый указатель + "Расстояние: 0 км 22 мин."
 */
@Composable
private fun RouteDistanceCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(s.cardBg)
            .border(s.strokeWidth, s.cardStroke, RoundedCornerShape(s.cardCorner))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Зеленая круглая иконка направления
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E).copy(alpha = 0.2f))
                .border(1.dp, Color(0xFF22C55E), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Navigation,
                contentDescription = "Маршрут",
                tint = Color(0xFF22C55E),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Расстояние: 0 км 22 мин.",
                color = s.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = s.fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Яндекс Навигатор · Маршрут",
                color = s.textSecondary,
                fontSize = 11.sp,
                fontFamily = s.fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
