package com.example.carlauncher.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.carlauncher.data.VideoItem
import com.example.carlauncher.data.VideoStore
import kotlinx.coroutines.launch

/**
 * Видео прямо в карточке.
 *
 * Играем через встроенный проигрыватель YouTube в WebView. Это
 * единственный способ показать видео внутри лаунчера на обычной сборке:
 * настоящее встраивание приложения требует прав прошивки, которых
 * у нас нет.
 *
 * Чего этот способ не умеет: входа в аккаунт, а значит подписок,
 * истории и рекомендаций. Часть роликов правообладатель запрещает
 * встраивать — для них показываем кнопку «Открыть в YouTube».
 */
@Composable
fun VideoCard(
    speedKmh: Int,
    blockOnDrive: Boolean,
    onSwitchCard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    val context = LocalContext.current

    var showList by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<VideoItem?>(null) }

    // Первое видео подставляем сразу, чтобы карточка не была пустой
    LaunchedEffect(VideoStore.items.size) {
        if (current == null) current = VideoStore.items.firstOrNull()
    }

    // Порог 5 км/ч, а не 0: GPS на стоянке шумит и показывает 1-2 км/ч,
    // от нуля картинка гасла бы у неподвижной машины.
    val driving = blockOnDrive && speedKmh > 5

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(Color.Black)
    ) {
        val item = current

        when {
            item == null -> EmptyVideoState(
                onAdd = { showList = true },
                onOpenApp = {
                    // Настоящее приложение с аккаунтом и подписками.
                    // Внутрь карточки его не пустить без прав прошивки,
                    // но запустить поверх лаунчера можно всегда.
                    com.example.carlauncher.data.AppRepository.launchFirstAvailable(
                        context,
                        com.example.carlauncher.data.AppRepository.VIDEO,
                        errorText = "YouTube не установлен"
                    )
                },
                onSwitchCard = onSwitchCard
            )

            else -> {
                YoutubePlayer(
                    videoId = item.id,
                    paused = driving,
                    modifier = Modifier.fillMaxSize()
                )

                // Шторка безопасности. Звук продолжает идти — так же
                // ведут себя штатные ГУ: видео на ходу нельзя, аудио можно.
                if (driving) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.94f)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = s.textSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Видео скрыто на ходу",
                            color = s.textPrimary,
                            fontSize = 18.sp,
                            fontFamily = s.fontFamily,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            text = "Звук продолжает играть",
                            color = s.textDim,
                            fontSize = 14.sp,
                            fontFamily = s.fontFamily
                        )
                    }
                }
            }
        }

        // ─── кнопки в углу ───
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CornerButton(Icons.Rounded.Add, "Список видео") { showList = true }
            CornerButton(Icons.Rounded.OpenInNew, "Открыть в YouTube") {
                current?.let {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${it.id}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }

        // Кубик на прежнем месте — возврат к машине
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(onClick = onSwitchCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                LauncherIcons.Cube,
                contentDescription = "Что показывать в карточке",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(24.dp)
            )
        }

        if (showList) {
            VideoListOverlay(
                onClose = { showList = false },
                onPick = { current = it; showList = false }
            )
        }
    }
}

/**
 * WebView с проигрывателем.
 *
 * AndroidView создаётся один раз и переиспользуется: пересоздание на
 * каждую перерисовку сбрасывало бы позицию воспроизведения.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YoutubePlayer(videoId: String, paused: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                // Без этого страница отдаётся в мобильной вёрстке
                // и проигрыватель занимает четверть карточки.
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = userAgentString.replace("; wv", "")
            }
            // Полноэкранный режим внутри карточки не нужен, но без
            // WebChromeClient видео в некоторых прошивках вообще не стартует.
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    // Переходы по ссылкам внутри плеера (на канал, «Смотреть
                    // на YouTube») уводим в приложение, иначе карточка
                    // превращается в мини-браузер.
                    val url = request?.url ?: return false
                    return runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        true
                    }.getOrDefault(false)
                }
            }
        }
    }

    // Страницу перезагружаем только при смене ролика
    LaunchedEffect(videoId) {
        val html = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}
            iframe{border:0;width:100%;height:100%;display:block}</style>
            </head><body>
            <iframe src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&modestbranding=1"
                    allow="autoplay; encrypted-media" allowfullscreen></iframe>
            </body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
    }

    // На ходу останавливаем ТОЛЬКО картинку: onPause() у WebView
    // замораживает отрисовку, звук при этом продолжает идти.
    LaunchedEffect(paused) {
        if (paused) webView.onPause() else webView.onResume()
    }

    // WebView держит ссылку на Activity — без явной очистки утечёт.
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }

    AndroidView(factory = { webView }, modifier = modifier)
}

/**
 * Пустая карточка: два равноправных пути.
 *
 * Открыть приложение — обычный запуск поверх лаунчера, со своим
 * аккаунтом и подписками. Добавить ссылку — воспроизведение внутри
 * карточки, но без аккаунта. Первое нужно чаще, поэтому оно основной
 * кнопкой.
 */
@Composable
private fun EmptyVideoState(
    onAdd: () -> Unit,
    onOpenApp: () -> Unit,
    onSwitchCard: () -> Unit
) {
    val s = LocalThemeSpec.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = s.textDim,
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = "Видео",
            color = s.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = s.fontFamily,
            modifier = Modifier.padding(top = 10.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 18.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(s.accent)
                    .clickable(onClick = onOpenApp)
                    .padding(horizontal = 22.dp, vertical = 13.dp)
            ) {
                Text(
                    "Открыть YouTube",
                    color = s.onAccent,
                    fontSize = 16.sp,
                    fontFamily = s.fontFamily
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 22.dp, vertical = 13.dp)
            ) {
                Text(
                    "Список ссылок",
                    color = s.textPrimary,
                    fontSize = 16.sp,
                    fontFamily = s.fontFamily
                )
            }
        }

        Text(
            text = "«Открыть» запускает приложение с вашим аккаунтом.\n«Список» проигрывает видео прямо здесь, но без аккаунта.",
            color = s.textDim,
            fontSize = 12.sp,
            fontFamily = s.fontFamily,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/** Список видео поверх карточки. */
@Composable
private fun VideoListOverlay(onClose: () -> Unit, onPick: (VideoItem) -> Unit) {
    val s = LocalThemeSpec.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(s.bg.first().copy(alpha = 0.98f))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Мои видео",
                color = s.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = s.fontFamily
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Закрыть",
                tint = s.textSecondary,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onClose)
                    .padding(8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(VideoStore.items, key = { it.id }) { v ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(s.cardBg)
                        .clickable { onPick(v) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NetworkThumb(
                        url = v.thumb,
                        modifier = Modifier
                            .width(112.dp)
                            .height(63.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            v.title,
                            color = s.textPrimary,
                            fontSize = 15.sp,
                            fontFamily = s.fontFamily,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (v.author.isNotBlank()) {
                            Text(v.author, color = s.textDim, fontSize = 12.sp, fontFamily = s.fontFamily)
                        }
                    }
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Удалить",
                        tint = s.textDim,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { VideoStore.remove(v.id) }
                            .padding(7.dp)
                    )
                }
            }
        }

        error?.let {
            Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp, fontFamily = s.fontFamily)
        }

        // ─── поле вставки ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (input.isEmpty()) {
                    Text(
                        "Вставьте ссылку на видео…",
                        color = s.textDim,
                        fontSize = 15.sp,
                        fontFamily = s.fontFamily
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    singleLine = true,
                    textStyle = TextStyle(color = s.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(s.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (busy) s.textDim else s.accent)
                    .clickable(enabled = !busy) {
                        busy = true
                        scope.launch {
                            error = VideoStore.add(input)
                            if (error == null) input = ""
                            busy = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Add, "Добавить", tint = s.onAccent, modifier = Modifier.size(22.dp))
            }
        }

        Text(
            "название и обложка подтянутся сами",
            color = s.textDim,
            fontSize = 12.sp,
            fontFamily = s.fontFamily,
            modifier = Modifier.padding(top = 6.dp, start = 18.dp)
        )
    }
}

@Composable
private fun CornerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
