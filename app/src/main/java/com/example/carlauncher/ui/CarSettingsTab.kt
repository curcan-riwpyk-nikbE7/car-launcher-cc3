package com.example.carlauncher.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.AudioProfiles
import com.example.carlauncher.data.AutoBrightness
import com.example.carlauncher.data.SteeringKeys

/**
 * Настройки автомобиля: кнопки руля, громкость, яркость.
 *
 * Вынесено отдельным экраном, а не плитками в общих настройках:
 * пунктов много и они связаны между собой по смыслу.
 */
@Composable
fun CarSettingsScreen(onClose: () -> Unit) {
    val s = LocalThemeSpec.current
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var learnKey by remember { mutableStateOf(false) }
    // Считаем до LazyColumn: внутри items() вызывать remember нельзя
    val assignedKeys = remember(revision) {
        SteeringKeys.assignments(context).entries.toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(s.bgBrush)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Автомобиль и звук",
                color = s.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = s.fontFamily
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.Close, "Закрыть",
                tint = s.textSecondary,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onClose)
                    .padding(9.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ─────────── тихий старт ───────────
            item {
                SectionTitle("Громкость")
            }
            item {
                ToggleRow(
                    icon = Icons.Rounded.VolumeDown,
                    title = "Тихий старт",
                    subtitle = "Не включать громче ${(AudioProfiles.softStartLevel(context) * 100).toInt()}% при заводке",
                    checked = AudioProfiles.softStartEnabled(context),
                    onCheck = { AudioProfiles.setSoftStartEnabled(context, it); revision++ }
                )
            }
            item {
                LevelRow(
                    label = "Потолок при старте",
                    value = AudioProfiles.softStartLevel(context),
                    onChange = { AudioProfiles.setSoftStartLevel(context, it); revision++ }
                )
            }

            // ─────────── навигатор ───────────
            item { SectionTitle("Навигатор") }
            item {
                ToggleRow(
                    icon = Icons.Rounded.Navigation,
                    title = "Приглушать музыку",
                    subtitle = "Во время голосовой подсказки",
                    checked = AudioProfiles.duckEnabled(context),
                    onCheck = { AudioProfiles.setDuckEnabled(context, it); revision++ }
                )
            }
            item {
                LevelRow(
                    label = "До какого уровня гасить",
                    value = AudioProfiles.duckLevel(context),
                    onChange = { AudioProfiles.setDuckLevel(context, it); revision++ }
                )
            }

            // ─────────── профили ───────────
            item { SectionTitle("Профили громкости") }
            item {
                ToggleRow(
                    icon = Icons.Rounded.Tune,
                    title = "Своя громкость для источников",
                    subtitle = "Радио, Bluetooth и медиа запоминаются отдельно",
                    checked = AudioProfiles.enabled(context),
                    onCheck = { AudioProfiles.setEnabled(context, it); revision++ }
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        AudioProfiles.Source.Radio to "Радио",
                        AudioProfiles.Source.Bluetooth to "Bluetooth",
                        AudioProfiles.Source.Media to "Медиа"
                    ).forEach { (src, label) ->
                        val saved = AudioProfiles.levelOf(context, src)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(s.cardBg)
                                .clickable {
                                    AudioProfiles.remember(context, src)
                                    revision++
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, color = s.textPrimary, fontSize = 14.sp, fontFamily = s.fontFamily)
                                Text(
                                    if (saved < 0) "запомнить текущую" else "${(saved * 100).toInt()}%",
                                    color = if (saved < 0) s.textDim else s.accent,
                                    fontSize = 12.sp,
                                    fontFamily = s.fontFamily
                                )
                            }
                        }
                    }
                }
            }

            // ─────────── яркость ───────────
            item { SectionTitle("Яркость") }
            item {
                ToggleRow(
                    icon = Icons.Rounded.Brightness6,
                    title = "По времени суток",
                    subtitle = if (AutoBrightness.isNight()) "Сейчас ночь" else "Сейчас день",
                    checked = AutoBrightness.enabled(context),
                    onCheck = { AutoBrightness.setEnabled(context, it); AutoBrightness.apply(context); revision++ }
                )
            }
            item {
                LevelRow(
                    label = "Днём",
                    value = AutoBrightness.dayLevel(context),
                    onChange = { AutoBrightness.setDayLevel(context, it); AutoBrightness.apply(context); revision++ }
                )
            }
            item {
                LevelRow(
                    label = "Ночью",
                    value = AutoBrightness.nightLevel(context),
                    onChange = { AutoBrightness.setNightLevel(context, it); AutoBrightness.apply(context); revision++ }
                )
            }

            // ─────────── кнопки руля ───────────
            item { SectionTitle("Кнопки на руле") }
            item {
                Text(
                    "Часть кнопок прошивка обрабатывает сама и до лаунчера " +
                        "не доводит — обычно это громкость и приём вызова. " +
                        "Такие назначить нельзя.",
                    color = s.textDim,
                    fontSize = 12.sp,
                    fontFamily = s.fontFamily,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(assignedKeys) { entry ->
                val code = entry.key
                val action = entry.value
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(s.cardBg)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        SteeringKeys.keyName(code),
                        color = s.textPrimary,
                        fontSize = 15.sp,
                        fontFamily = s.fontFamily
                    )
                    Spacer(Modifier.weight(1f))
                    Text(action.title, color = s.accent, fontSize = 14.sp, fontFamily = s.fontFamily)
                    Icon(
                        Icons.Rounded.Close, "Убрать",
                        tint = s.textDim,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable {
                                SteeringKeys.assign(context, code, SteeringKeys.Action.None)
                                revision++
                            }
                            .padding(6.dp)
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(s.accent)
                        .clickable { learnKey = true }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Назначить кнопку", color = s.onAccent, fontSize = 15.sp, fontFamily = s.fontFamily)
                }
            }
        }
    }

    if (learnKey) {
        KeyLearnDialog(
            onDone = { learnKey = false; revision++ },
            onDismiss = { learnKey = false }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    val s = LocalThemeSpec.current
    Text(
        text,
        color = s.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = s.fontFamily,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp)
    )
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheck: (Boolean) -> Unit
) {
    val s = LocalThemeSpec.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(s.cardBg)
            .clickable { onCheck(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (checked) s.accent else s.textDim, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, color = s.textPrimary, fontSize = 15.sp, fontFamily = s.fontFamily)
            Text(subtitle, color = s.textDim, fontSize = 12.sp, fontFamily = s.fontFamily)
        }
        ThemedSwitch(checked, onCheck)
    }
}

/** Ползунок 0..1. Тянуть можно за всю полосу — попасть в машине проще. */
@Composable
private fun LevelRow(label: String, value: Float, onChange: (Float) -> Unit) {
    val s = LocalThemeSpec.current
    var width by remember { mutableStateOf(1f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(s.cardBg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row {
            Text(label, color = s.textSecondary, fontSize = 13.sp, fontFamily = s.fontFamily)
            Spacer(Modifier.weight(1f))
            Text("${(value * 100).toInt()}%", color = s.accent, fontSize = 13.sp, fontFamily = s.fontFamily)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(top = 8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .pointerInput(Unit) {
                    width = size.width.toFloat()
                    detectDragGestures(
                        onDragStart = { offset -> onChange((offset.x / width).coerceIn(0.05f, 1f)) }
                    ) { change, drag ->
                        onChange((value + drag.x / width).coerceIn(0.05f, 1f))
                        change.consume()
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value)
                    .height(26.dp)
                    .clip(CircleShape)
                    .background(s.accent)
            )
        }
    }
}

/**
 * Обучение кнопке: ждём нажатия и предлагаем действие.
 *
 * Своего перехвата клавиш у диалога нет — Compose их не видит. Поэтому
 * показываем список известных кодов: на ГУ руль обычно шлёт стандартные
 * медиа-клавиши, а не произвольные.
 */
@Composable
private fun KeyLearnDialog(onDone: () -> Unit, onDismiss: () -> Unit) {
    val s = LocalThemeSpec.current
    val context = LocalContext.current
    var pickedKey by remember { mutableStateOf<Int?>(null) }

    val commonKeys = listOf(
        android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
        android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        android.view.KeyEvent.KEYCODE_VOICE_ASSIST,
        android.view.KeyEvent.KEYCODE_SEARCH,
        android.view.KeyEvent.KEYCODE_CALL,
        android.view.KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
        android.view.KeyEvent.KEYCODE_DPAD_UP,
        android.view.KeyEvent.KEYCODE_DPAD_DOWN,
        android.view.KeyEvent.KEYCODE_DPAD_CENTER
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(s.cardCorner))
                .background(s.cardBg)
                .clickable(enabled = false) {}
                .padding(22.dp)
        ) {
            Text(
                if (pickedKey == null) "Какая кнопка" else "Что она делает",
                color = s.textPrimary,
                fontSize = 19.sp,
                fontFamily = s.fontFamily
            )

            if (pickedKey == null) {
                LazyColumn(
                    modifier = Modifier.height(300.dp).padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(commonKeys) { code ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { pickedKey = code }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                SteeringKeys.keyName(code),
                                color = s.textPrimary,
                                fontSize = 15.sp,
                                fontFamily = s.fontFamily
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp).padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(SteeringKeys.Action.entries.filter { it != SteeringKeys.Action.None }) { act ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable {
                                    SteeringKeys.assign(context, pickedKey!!, act)
                                    onDone()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(act.title, color = s.textPrimary, fontSize = 15.sp, fontFamily = s.fontFamily)
                        }
                    }
                }
            }
        }
    }
}
