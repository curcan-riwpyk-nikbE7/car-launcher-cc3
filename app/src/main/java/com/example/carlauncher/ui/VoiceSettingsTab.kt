package com.example.carlauncher.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.carlauncher.data.SettingsStore
import com.example.carlauncher.voice.ModelDownloader
import com.example.carlauncher.voice.VoiceCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Вкладка «Голос».
 *
 * Помощник живёт в MainActivity, а настройки — отдельная активность,
 * поэтому напрямую дёргать его отсюда нельзя. Вместо этого работаем
 * с тем, что общее: разрешение микрофона, файл модели на диске,
 * системный движок TTS и сохранённые настройки.
 *
 * Так вкладка честно показывает состояние и чинит то, что сломано,
 * даже когда помощник в этот момент не запущен.
 */
@Composable
fun VoiceTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- микрофон ---
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { micGranted = it }

    // --- модель ---
    var modelReady by remember { mutableStateOf(ModelDownloader.isInstalled(context)) }
    var progress by remember { mutableStateOf<Float?>(null) }

    // --- русский голос ---
    var ttsOk by remember { mutableStateOf<Boolean?>(null) }
    CheckRussianTts(context) { ttsOk = it }

    var showCommands by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SettingTile(
                icon = Icons.Rounded.Mic,
                title = "Микрофон",
                subtitle = if (micGranted) "Доступ выдан" else "Нажмите, чтобы разрешить",
                accentIcon = micGranted,
                onClick = {
                    if (micGranted) {
                        // Уже выдан — ведём в системные настройки приложения,
                        // вдруг пользователь хочет отозвать.
                        openAppSettings(context)
                    } else {
                        micRequest.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }

        item {
            SettingTile(
                icon = Icons.Rounded.Download,
                title = "Модель распознавания",
                subtitle = when {
                    progress != null -> "Загрузка ${((progress ?: 0f) * 100).toInt()}%"
                    modelReady -> "Установлена, работает офлайн"
                    else -> "Нет · нажмите, нужен Wi-Fi (44 МБ)"
                },
                accentIcon = modelReady,
                onClick = {
                    if (progress != null || modelReady) return@SettingTile
                    progress = 0f
                    scope.launch {
                        val ok = ModelDownloader.download(context) { p -> progress = p }
                        progress = null
                        modelReady = ok
                    }
                },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }

        item {
            val wake = SettingsStore.voiceWake.value
            SettingTile(
                icon = Icons.Rounded.Hearing,
                title = "Слово активации",
                subtitle = if (wake) "«Привет, машина»" else "Только кнопкой на панели",
                accentIcon = wake,
                trailing = { ThemedSwitch(wake) { SettingsStore.setVoiceWake(it) } },
                onClick = { SettingsStore.setVoiceWake(!wake) },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }

        item {
            SettingTile(
                icon = Icons.Rounded.RecordVoiceOver,
                title = "Голосовые ответы",
                subtitle = when (ttsOk) {
                    true -> "Русский голос есть"
                    false -> "Нет русского голоса · нажмите"
                    null -> "Проверяю…"
                },
                accentIcon = ttsOk == true,
                onClick = { openTtsSettings(context) },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }

        item {
            SettingTile(
                icon = Icons.Rounded.GraphicEq,
                title = if (showCommands) "Команды" else "Что понимает",
                subtitle = if (showCommands) COMMANDS_HINT
                else "${VoiceCommands.grammar.size} фраз · нажмите",
                onClick = { showCommands = !showCommands },
                modifier = Modifier.fillMaxWidth().height(196.dp)
            )
        }
    }
}

/** Короткая шпаргалка. Полный список — в грамматике распознавателя. */
private const val COMMANDS_HINT =
    "выключи экран · включи музыку · пауза · следующий трек · " +
        "громче · тише · громкость 50 · открой карты · открой ютуб · " +
        "включи радио · ночной режим · какая скорость · домой"

/**
 * Проверка русского голоса.
 *
 * Движок TTS инициализируется асинхронно, поэтому спрашиваем через
 * колбэк и обязательно освобождаем — иначе на некоторых прошивках
 * он остаётся висеть и занимает аудиовыход.
 */
@Composable
private fun CheckRussianTts(context: Context, onResult: (Boolean) -> Unit) {
    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        runCatching {
            tts = TextToSpeech(context) { status ->
                val ok = status == TextToSpeech.SUCCESS && runCatching {
                    val r = tts?.setLanguage(Locale("ru", "RU"))
                    r == TextToSpeech.LANG_AVAILABLE ||
                        r == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                        r == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                }.getOrDefault(false)
                onResult(ok)
            }
        }.onFailure { onResult(false) }

        onDispose {
            runCatching {
                tts?.stop()
                tts?.shutdown()
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Настройки синтеза речи, а если их нет — страница RHVoice. */
private fun openTtsSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent("com.android.settings.TTS_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.github.olga_yakovleva.rhvoice.android")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
