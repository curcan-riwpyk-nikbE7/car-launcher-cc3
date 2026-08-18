package com.example.carlauncher.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import com.example.carlauncher.data.ShortcutStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.R
import com.example.carlauncher.data.AppInfo
import com.example.carlauncher.data.AppRepository

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsScreen(
    apps: List<AppInfo>,
    loading: Boolean = false,
    onBack: () -> Unit = {},
    onAddToFavorites: (AppInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val s = LocalThemeSpec.current
    val haptic = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<AppInfo?>(null) }

    // Скрытые приложения не показываем. Список живёт в настройках,
    // само приложение с устройства не удаляется.
    val store = remember { ShortcutStore(context) }
    var hidden by remember { mutableStateOf(store.hiddenApps()) }

    val filtered = remember(query, apps, hidden) {
        val visible = apps.filter { it.packageName !in hidden }
        if (query.isBlank()) visible
        else visible.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(s.bgBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Кнопка «Назад»: на многих ГУ системные кнопки скрыты,
                // без неё из списка было не выйти.
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        // Градиент вместо плоской заливки: кнопка стала
                        // объёмной и попадает в стиль остальных элементов.
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            )
                        )
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back),
                        tint = s.textPrimary, modifier = Modifier.size(24.dp)
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(stringResource(R.string.search_hint), color = s.textSecondary, fontFamily = s.fontFamily)
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = s.textSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(s.cardCorner),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = s.cardBg,
                        unfocusedContainerColor = s.cardBg,
                        focusedBorderColor = s.accent,
                        unfocusedBorderColor = s.cardStroke,
                        focusedTextColor = s.textPrimary,
                        unfocusedTextColor = s.textPrimary,
                        cursorColor = s.accent
                    ),
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .fillMaxWidth(0.55f)
                )

                if (apps.isNotEmpty()) {
                    Text(
                        text = "${filtered.size}",
                        color = s.textDim,
                        fontSize = 13.sp,
                        fontFamily = s.fontFamily,
                        modifier = Modifier.padding(start = 14.dp)
                    )
                }
            }

            // BoxWithConstraints вместо Box: сетке нужна фактическая
            // высота, чтобы посчитать, сколько рядов поместится.
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(top = 10.dp)) {
                when {
                    // Список ещё грузится — раньше был просто пустой экран
                    loading && apps.isEmpty() -> EmptyState(
                        loading = true,
                        title = stringResource(R.string.loading_apps),
                        subtitle = "Это занимает пару секунд"
                    )
                    apps.isEmpty() -> EmptyState(
                        loading = false,
                        title = stringResource(R.string.apps_not_found),
                        subtitle = "Возможно, система ещё не завершила загрузку"
                    )
                    filtered.isEmpty() -> EmptyState(
                        loading = false,
                        title = stringResource(R.string.nothing_found),
                        subtitle = "По запросу «${query.trim()}» совпадений нет"
                    )
                    else -> {
                        // Страницы листаются вбок. Вертикальная прокрутка
                        // на ходу неудобна: список уезжает от тряски и
                        // палец теряет место. Страницы же всегда встают
                        // на одну и ту же позицию.
                        //
                        // Четыре колонки на 1280 — компромисс: пять делают
                        // плитку такой узкой, что длинные названия режутся
                        // на половине слова.
                        //
                        // Число рядов раньше было жёстко задано тройкой,
                        // и на экране оставалась пустая треть: константу
                        // подбирали, когда сверху висела панель ГУ. Теперь
                        // считаем от фактической высоты — сетка заполняет
                        // всё, что есть, и на 55 приложений выходит три
                        // страницы вместо пяти.
                        val columns = 4
                        val rows = with(LocalDensity.current) {
                            // 104 dp — плитка (76 иконка + отступы по 14),
                            // 12 dp — промежуток между рядами.
                            val rowHeight = 116.dp.toPx()
                            (maxHeight.toPx() / rowHeight).toInt().coerceIn(3, 6)
                        }
                        val perPage = columns * rows
                        val pageCount = (filtered.size + perPage - 1) / perPage
                        val pagerState = rememberPagerState(
                            pageCount = { pageCount.coerceAtLeast(1) }
                        )

                        Column(modifier = Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.weight(1f)
                            ) { page ->
                                val from = page * perPage
                                val slice = filtered.drop(from).take(perPage)
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    userScrollEnabled = false,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(slice, key = { it.packageName + it.activityName }) { app ->
                            // Плитка горизонтальная: иконка слева в квадрате,
                            // подпись справа в одну строку — как у CC3.
                            // Вертикальная сетка с подписью в две строки
                            // делала ряды разной высоты и выглядела рвано.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .cardDepth(
                                        corner = s.cardCorner,
                                        accent = s.accent,
                                        background = s.cardBg,
                                        stroke = s.cardStroke.copy(alpha = 0.35f),
                                        strokeWidth = s.strokeWidth,
                                        elevation = 5.dp
                                    )
                                    .combinedClickable(
                                        onClick = { AppRepository.launch(context, app) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            actionsFor = app
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                // Иконка была 46 dp в контейнере 64 — на экране
                                // магнитолы с высокой плотностью это меньше
                                // сантиметра, попасть на ходу трудно. Теперь
                                // 54 в контейнере 76, и подложка градиентная:
                                // плоская заливка делала иконку наклеенной.
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(s.iconCorner))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.White.copy(alpha = 0.12f),
                                                    Color.White.copy(alpha = 0.04f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIcon(app.icon, app.label, Modifier.size(54.dp))
                                }
                                Text(
                                    text = app.label,
                                    color = s.textPrimary,
                                    fontSize = 17.sp,
                                    fontFamily = s.fontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(start = 14.dp)
                                        .weight(1f)
                                )
                            }
                                    }
                                }
                            }

                            // Точки-страницы. При одной странице не рисуем:
                            // индикатор из единственной точки только сбивает.
                            if (pageCount > 1) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(top = 10.dp)
                                ) {
                                    repeat(pageCount) { i ->
                                        val active = i == pagerState.currentPage
                                        Box(
                                            modifier = Modifier
                                                .size(if (active) 9.dp else 7.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (active) s.accent
                                                    else s.textPrimary.copy(alpha = 0.25f)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    actionsFor?.let { app ->
        AppActionsDialog(
            app = app,
            isSystem = AppRepository.isSystemApp(context, app.packageName),
            onAddToFavorites = { onAddToFavorites(app); actionsFor = null },
            onAppInfo = { AppRepository.openAppInfo(context, app.packageName); actionsFor = null },
            onUninstall = { AppRepository.requestUninstall(context, app.packageName); actionsFor = null },
            onHide = {
                store.hideApp(app.packageName)
                hidden = store.hiddenApps()
                actionsFor = null
            },
            onDismiss = { actionsFor = null }
        )
    }
}

/** Заглушка вместо пустого экрана: загрузка, пустой список или пустой поиск. */
@Composable
private fun EmptyState(loading: Boolean, title: String, subtitle: String) {
    val s = LocalThemeSpec.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = s.accent, strokeWidth = 3.dp,
                modifier = Modifier.size(38.dp))
        } else {
            Icon(Icons.Rounded.SearchOff, null, tint = s.textDim, modifier = Modifier.size(44.dp))
        }
        Text(
            text = title, color = s.textPrimary, fontSize = 16.sp,
            fontFamily = s.fontFamily, modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            text = subtitle, color = s.textSecondary, fontSize = 12.sp,
            fontFamily = s.fontFamily, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
