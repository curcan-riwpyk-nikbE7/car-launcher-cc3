package com.example.carlauncher.ui

import android.app.ActivityView
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.carlauncher.data.AppIntents
import com.example.carlauncher.data.SettingsStore

private const val TAG = "EmbeddedAppView"

/**
 * Встраивание нативного приложения (Яндекс Карты, Навигатор, YouTube)
 * в карточку главного экрана через системный компонент Android 10 `android.app.ActivityView`
 * (архитектура LecoAuto / DriveDeck).
 *
 * Особенности:
 *  - Прямой аппаратно-ускоренный рендеринг внутри иерархии View лаунчера;
 *  - Отсутствие системных рамок, заголовков и кнопок от свободных плавающих окон ([ 🗖 ] [ ✕ ]);
 *  - Автоматическая трансляция нативных тач-событий (жесты зума, скролла карты) силами AOSP InputDispatcher;
 *  - Запуск через прямой LaunchIntent главного экрана, исключающий сброс Trampoline-активностей на дисплей 0.
 */
@Composable
fun EmbeddedAppView(
    packageName: String,
    modifier: Modifier = Modifier,
    onFailed: () -> Unit = {}
) {
    val context = LocalContext.current
    val s = LocalThemeSpec.current

    val isActivityViewSupported = remember {
        try {
            Class.forName("android.app.ActivityView")
            true
        } catch (t: Throwable) {
            false
        }
    }

    if (!isActivityViewSupported) {
        FallbackNotice(onFailed, hadPermission = false)
        return
    }

    var activityViewRef by remember { mutableStateOf<ActivityView?>(null) }
    var isReady by remember { mutableStateOf(false) }
    var currentLaunchedPkg by remember { mutableStateOf<String?>(null) }

    // Перезапуск приложения при смене назначенного пакета (например, переход с Карт на YouTube)
    LaunchedEffect(packageName, isReady) {
        val av = activityViewRef
        if (isReady && av != null && currentLaunchedPkg != packageName) {
            startTargetApp(context, av, packageName)
            currentLaunchedPkg = packageName
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(s.carCardBg)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ActivityView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setCallback(object : ActivityView.StateCallback() {
                        override fun onActivityViewReady(view: ActivityView) {
                            Log.i(TAG, "ActivityView ready! VirtualDisplayId=${view.virtualDisplayId}, starting $packageName")
                            activityViewRef = view
                            isReady = true
                            startTargetApp(ctx, view, packageName)
                            currentLaunchedPkg = packageName
                        }

                        override fun onActivityViewDestroyed(view: ActivityView) {
                            Log.i(TAG, "ActivityView destroyed for $packageName")
                            isReady = false
                            activityViewRef = null
                            currentLaunchedPkg = null
                        }

                        override fun onTaskCreated(taskId: Int, componentName: ComponentName?) {
                            Log.i(TAG, "ActivityView task created: id=$taskId comp=$componentName")
                        }

                        override fun onTaskMovedToFront(taskId: Int) {
                            Log.i(TAG, "ActivityView task moved to front: id=$taskId")
                        }

                        override fun onTaskRemovalStarted(taskId: Int) {
                            Log.i(TAG, "ActivityView task removal started: id=$taskId")
                        }
                    })
                }
            },
            onRelease = { view ->
                Log.i(TAG, "Releasing ActivityView for $packageName")
                runCatching { view.release() }
                activityViewRef = null
                isReady = false
                currentLaunchedPkg = null
            }
        )
    }
}

/**
 * Запуск целевого приложения напрямую в ActivityView.
 *
 * КРИТИЧНО: Используем прямой LaunchIntent главного экрана (MainActivity) пакета,
 * а не deep link URI схемы (yandexmaps://, yandexnavi://), так как глубокие ссылки
 * запускают промежуточные Trampoline-активности, которые немедленно закрываются через finish()
 * и приводят к чёрному экрану.
 */
private fun startTargetApp(context: Context, view: ActivityView, packageName: String) {
    runCatching {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: AppIntents.bestIntent(context, packageName)

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            view.startActivity(launchIntent)
            Log.i(TAG, "startActivity($packageName) успешно передан в ActivityView")
        } else {
            Log.w(TAG, "LaunchIntent не найден для пакета $packageName")
        }
    }.onFailure { e ->
        Log.e(TAG, "Ошибка при старте $packageName в ActivityView", e)
    }
}

@Composable
private fun FallbackNotice(onFailed: () -> Unit, hadPermission: Boolean) {
    val s = LocalThemeSpec.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.Warning, null,
            tint = s.textDim, modifier = Modifier.size(26.dp)
        )
        Text(
            text = if (hadPermission)
                "Приложение не удалось встроить"
            else
                "Встраивание доступно только в системной сборке Android 10",
            color = s.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            fontFamily = s.fontFamily,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(s.accent.copy(alpha = 0.15f))
                    .clickable { SettingsStore.setCardContentMode(SettingsStore.CARD_MODE_MAP) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Живая карта (GPS)",
                    color = s.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = s.fontFamily
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { SettingsStore.setCardContentMode(SettingsStore.CARD_MODE_SPEED) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Спидометр",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = s.fontFamily
                )
            }
        }
    }
    LaunchedEffect(Unit) { onFailed() }
}
