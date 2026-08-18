package com.example.carlauncher.ui

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.mutableIntStateOf
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.FreeformLauncher
import com.example.carlauncher.data.SystemPrivileges
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

/**
 * Что именно система нам разрешила.
 *
 * Появился, потому что «не работает встраивание» — слишком общая жалоба:
 * причин может быть четыре, и снаружи они выглядят одинаково. Здесь
 * видно, какая именно сработала.
 */
@Composable
fun DiagnosticsScreen(onClose: () -> Unit) {
    val s = LocalThemeSpec.current
    val context = LocalContext.current

    val rows = remember { collect(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(s.bgBrush)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Диагностика прав",
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

        Text(
            "Если карта не показывается в карточке — причина здесь",
            color = s.textDim,
            fontSize = 13.sp,
            fontFamily = s.fontFamily,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            // Подбор способа управления громкостью.
            //
            // Проверить успех программно нельзя: getStreamVolume вернёт
            // изменившееся значение даже когда усилитель команду
            // проигнорировал. Судить может только человек — на слух,
            // поэтому кнопки и живут здесь.
            item { VolumeTuner() }

            // Сырые значения источников напряжения.
            //
            // Блок с вольтами в панели скрывается, когда данных нет —
            // выдуманное число хуже пустого места. Но снаружи это выглядит
            // одинаково и в случае «MCU молчит», и в случае «мы читаем
            // не то имя». Здесь видно, какой именно источник что вернул.
            item { VoltageSources() }

            items(rows) { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(s.cardBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(r.title, color = s.textPrimary, fontSize = 15.sp, fontFamily = s.fontFamily)
                        if (r.hint.isNotBlank()) {
                            Text(r.hint, color = s.textDim, fontSize = 11.sp, fontFamily = s.fontFamily)
                        }
                    }
                    Text(
                        text = r.value,
                        color = when (r.state) {
                            State.Good -> Color(0xFF4CD07D)
                            State.Bad -> Color(0xFFFF8A65)
                            State.Info -> s.textSecondary
                        },
                        fontSize = 14.sp,
                        fontFamily = if (r.mono) FontFamily.Monospace else s.fontFamily
                    )
                }
            }
        }
    }
}

private enum class State { Good, Bad, Info }

private data class Row3(
    val title: String,
    val value: String,
    val state: State,
    val hint: String = "",
    val mono: Boolean = false
)

private fun collect(context: Context): List<Row3> {
    val out = mutableListOf<Row3>()

    // ─── встраивание ───
    val embed = SystemPrivileges.canEmbedActivities(context)
    out += Row3(
        "Встраивание в карточку",
        if (embed) "работает" else "нет прав",
        if (embed) State.Good else State.Bad,
        if (embed) "карта показывается прямо в блоке"
        else "нужна подпись ключом прошивки"
    )

    val uid = Process.myUid()
    out += Row3(
        "Системный пользователь",
        if (uid == 1000) "да" else "нет (uid $uid)",
        if (uid == 1000) State.Good else State.Bad,
        "без него VirtualDisplay не принимает чужое окно"
    )

    val secure = SystemPrivileges.canWriteSecureSettings(context)
    out += Row3(
        "Изменение системных настроек",
        if (secure) "разрешено" else "нет",
        if (secure) State.Good else State.Info,
        "нужно, чтобы карта верстались по размеру блока"
    )

    val freeform = FreeformLauncher.isAvailable(context)
    out += Row3(
        "Плавающие окна",
        if (freeform) "включены" else "выключены",
        if (freeform) State.Good else State.Info,
        "запасной путь, если встраивание недоступно"
    )

    // ─── питание автомобиля ───
    // Показываем источник, а не только цифру: если вольты не пришли,
    // сразу видно, молчит ли MCU или не найдены свойства и sysfs.
    val (volts, src) = com.example.carlauncher.data.CarPower.pollVoltage()
    out += Row3(
        "Напряжение бортовой сети",
        if (volts > 0f) "%.1f В".format(volts) else "не отдаётся",
        if (volts > 0f) State.Good else State.Info,
        if (volts > 0f) "источник: $src"
        else "MCU не рассылает — ждём broadcast при смене зажигания"
    )

    // ─── про устройство ───
    out += Row3("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", State.Info)
    out += Row3("Платформа", Build.HARDWARE, State.Info, mono = true)

    // Тег прошивки — по нему видно, каким ключом она подписана.
    // test-keys значит стандартный AOSP testkey, release-keys —
    // собственный ключ производителя, и тогда шансов нет.
    val tags = Build.TAGS ?: "?"
    out += Row3(
        "Подпись прошивки",
        tags,
        if (tags.contains("test")) State.Good else State.Bad,
        if (tags.contains("test")) "стандартный ключ, наша подпись подходит"
        else "свой ключ производителя",
        mono = true
    )

    out += Row3("Сборка", Build.DISPLAY.take(40), State.Info, mono = true)

    // ─── наша подпись ───
    // Берём из BuildIdentity: там же, откуда обновление узнаёт,
    // какой файл ему скачивать. Одно место — один ответ.
    val id = com.example.carlauncher.data.BuildIdentity.current(context)

    out += Row3(
        "Подпись лаунчера",
        if (id.certSha256.isEmpty()) "не прочиталась" else id.short,
        if (id.certSha256.isEmpty()) State.Bad else State.Info,
        "a40da80a — ключ AOSP testkey, fe3acea5 — свой ключ",
        mono = true
    )

    out += Row3(
        "Какая сборка стоит",
        id.title,
        State.Info,
        "обновление скачивает файл ровно с этими приметами"
    )

    return out
}

/**
 * Подбор рабочего способа управления громкостью.
 *
 * На этой магнитоле звук идёт через внешний усилитель, которым
 * управляет MCU. Android держит свой уровень, ни к чему не
 * подключённый: жест показывал полосу на экране, значение менялось,
 * а громкость оставалась прежней.
 *
 * Какой обходной путь сработает — зависит от прошивки, и определить
 * это изнутри невозможно. Поэтому способы перебираются вручную:
 * нажал, послушал, отметил рабочий. Дальше лаунчер использует
 * только его.
 */
@Composable
private fun VolumeTuner() {
    val s = LocalThemeSpec.current
    val context = LocalContext.current
    var current by remember {
        mutableStateOf(com.example.carlauncher.data.VolumeBridge.method(context))
    }
    val fixed = remember { com.example.carlauncher.data.VolumeBridge.isFixed(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(s.cardBg)
            .padding(16.dp)
    ) {
        Text(
            "Громкость жестами",
            color = s.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = s.fontFamily
        )
        Text(
            if (fixed)
                "Система сообщает: громкость управляется внешним трактом. " +
                    "Обычный способ не сработает — выберите другой"
            else
                "Нажмите + или − и послушайте. Отметьте способ, от которого меняется звук",
            color = s.textDim,
            fontSize = 12.sp,
            fontFamily = s.fontFamily,
            modifier = Modifier.padding(top = 3.dp, bottom = 10.dp)
        )

        com.example.carlauncher.data.VolumeBridge.Method.entries
            .filter { it != com.example.carlauncher.data.VolumeBridge.Method.Auto }
            .forEach { m ->
                val selected = current == m
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) s.accent.copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.05f)
                        )
                        .clickable {
                            com.example.carlauncher.data.VolumeBridge.setMethod(context, m)
                            current = m
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            m.title,
                            color = if (selected) s.accent else s.textPrimary,
                            fontSize = 14.sp,
                            fontFamily = s.fontFamily
                        )
                        Text(
                            m.hint,
                            color = s.textDim,
                            fontSize = 11.sp,
                            fontFamily = s.fontFamily
                        )
                    }
                    listOf(false, true).forEach { up ->
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable {
                                    com.example.carlauncher.data.VolumeBridge.test(context, m, up)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (up) "+" else "−",
                                color = s.textPrimary,
                                fontSize = 20.sp,
                                fontFamily = s.fontFamily
                            )
                        }
                    }
                }
            }
    }
}

/**
 * Что вернул каждый источник напряжения.
 *
 * Без этого списка «вольты не показываются» — слишком общая жалоба.
 * Причин три, и снаружи они выглядят одинаково: свойства нет вовсе,
 * оно есть но пустое, или значение приходит в неожиданном виде
 * и отбраковывается при пересчёте.
 *
 * Прочерк значит «источника нет». Любое другое содержимое — это
 * то, что реально отдала система, без обработки. По нему сразу
 * понятно, какое имя живое и как править разбор.
 */
@Composable
private fun VoltageSources() {
    val s = LocalThemeSpec.current
    var expanded by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val sources = remember(revision) { com.example.carlauncher.data.CarPower.rawSources() }
    val alive = sources.count { it.second != "—" && it.second != "нет файла" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(s.cardBg)
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Источники напряжения",
                    color = s.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = s.fontFamily
                )
                Text(
                    if (alive > 0) "отвечают: $alive из ${sources.size} · нажмите, чтобы раскрыть"
                    else "все молчат · нажмите, чтобы раскрыть",
                    color = if (alive > 0) s.textDim else Color(0xFFFF8A65),
                    fontSize = 12.sp,
                    fontFamily = s.fontFamily
                )
            }
            Text(
                if (expanded) "свернуть" else "показать",
                color = s.accent,
                fontSize = 13.sp,
                fontFamily = s.fontFamily
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                sources.forEach { (name, value) ->
                    val empty = value == "—" || value == "нет файла" || value == "нет доступа"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            name,
                            color = if (empty) s.textDim else s.textPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            value,
                            color = if (empty) s.textDim else Color(0xFF4CD07D),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(s.accent.copy(alpha = 0.18f))
                        .clickable { revision++ }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        "Обновить",
                        color = s.accent,
                        fontSize = 14.sp,
                        fontFamily = s.fontFamily
                    )
                }
            }
        }
    }
}
