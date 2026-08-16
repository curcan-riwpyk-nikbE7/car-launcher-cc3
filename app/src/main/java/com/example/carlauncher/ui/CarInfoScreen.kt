package com.example.carlauncher.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.Maintenance
import com.example.carlauncher.data.TripComputer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран «Автомобиль»: пробег, ТО, журнал поездок.
 *
 * Пробег считается по GPS: с шины автомобиля данные без прав прошивки
 * не взять. Погрешность около 2-3% — для напоминания о замене масла
 * этого достаточно, поэтому цифру честно подписываем «по GPS» и даём
 * задать реальное значение с одометра вручную.
 */
@Composable
fun CarInfoScreen(onClose: () -> Unit) {
    val s = LocalThemeSpec.current
    var editOdo by remember { mutableStateOf(false) }
    var editInterval by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0) }

    val totalKm = remember(revision, Maintenance.totalM.value) {
        (Maintenance.totalM.value / 1000f).toInt()
    }
    val left = remember(revision, Maintenance.totalM.value, Maintenance.intervalKm.value) {
        Maintenance.kmLeft
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(s.bgBrush)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Автомобиль",
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ─── пробег ───
            InfoCard(
                icon = Icons.Rounded.Speed,
                title = "Пробег по GPS",
                value = "$totalKm км",
                hint = "нажмите, чтобы указать по одометру",
                onClick = { editOdo = true },
                modifier = Modifier.weight(1f)
            )

            // ─── ТО ───
            InfoCard(
                icon = Icons.Rounded.Build,
                title = if (left >= 0) "До ТО" else "ТО просрочено",
                value = if (left >= 0) "$left км" else "${-left} км",
                hint = "интервал ${Maintenance.intervalKm.value} км",
                accent = left < 500,
                onClick = { editInterval = true },
                modifier = Modifier.weight(1f)
            )
        }

        // ─── текущая поездка ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(s.cardCorner))
                .background(s.cardBg)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Stat("Поездка", "%.1f км".format(TripComputer.distanceM.value / 1000f))
            Stat("В пути", "${TripComputer.movingMs.value / 60000} мин")
            Stat("Средняя", "${TripComputer.averageKmh} км/ч")
            Stat("Максимум", "${TripComputer.maxKmh.value} км/ч")

            // Напряжение здесь же, а не только в строке статуса: на этом
            // экране на него смотрят осознанно, а не мельком.
            val power by com.example.carlauncher.data.CarPower.rememberPower()
            if (power.hasVoltage) {
                Stat("Бортсеть", "%.1f В".format(power.volts))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 18.dp)
        ) {
            Icon(Icons.Rounded.Timeline, null, tint = s.textSecondary, modifier = Modifier.size(18.dp))
            Text(
                "Последние поездки",
                color = s.textSecondary,
                fontSize = 15.sp,
                fontFamily = s.fontFamily,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Сбросить ТО",
                color = s.accent,
                fontSize = 14.sp,
                fontFamily = s.fontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { Maintenance.markServiced(); revision++ }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        val trips = remember(revision) { Maintenance.trips() }
        if (trips.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Поездок пока нет",
                    color = s.textDim,
                    fontSize = 15.sp,
                    fontFamily = s.fontFamily
                )
            }
        } else {
            val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
            LazyColumn(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(trips) { t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(s.cardBg)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            fmt.format(Date(t.startedAt)),
                            color = s.textSecondary,
                            fontSize = 14.sp,
                            fontFamily = s.fontFamily
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "%.1f км".format(t.distanceKm),
                            color = s.textPrimary,
                            fontSize = 15.sp,
                            fontFamily = s.fontFamily
                        )
                        Text(
                            "  ${t.minutes} мин  ·  ${t.avgKmh} км/ч",
                            color = s.textDim,
                            fontSize = 13.sp,
                            fontFamily = s.fontFamily
                        )
                    }
                }
            }
        }
    }

    if (editOdo) {
        NumberDialog(
            title = "Пробег по одометру",
            hint = "Введите километры с приборной панели",
            initial = totalKm.toString(),
            suffix = "км",
            onOk = { Maintenance.setTotalKm(it); revision++; editOdo = false },
            onDismiss = { editOdo = false }
        )
    }

    if (editInterval) {
        NumberDialog(
            title = "Интервал ТО",
            hint = "Через сколько километров напомнить",
            initial = Maintenance.intervalKm.value.toString(),
            suffix = "км",
            onOk = { Maintenance.setInterval(it); revision++; editInterval = false },
            onDismiss = { editInterval = false }
        )
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    hint: String,
    accent: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalThemeSpec.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(s.cardCorner))
            .background(s.cardBg)
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, null,
                tint = if (accent) Color(0xFFFF8A65) else s.accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                title,
                color = s.textSecondary,
                fontSize = 14.sp,
                fontFamily = s.fontFamily,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(
            value,
            color = if (accent) Color(0xFFFF8A65) else s.textPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
            fontFamily = s.fontFamily,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(hint, color = s.textDim, fontSize = 12.sp, fontFamily = s.fontFamily)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val s = LocalThemeSpec.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = s.textPrimary, fontSize = 19.sp, fontFamily = s.fontFamily)
        Text(label, color = s.textDim, fontSize = 12.sp, fontFamily = s.fontFamily)
    }
}

/** Ввод числа. Экранная клавиатура на ГУ есть, годится. */
@Composable
private fun NumberDialog(
    title: String,
    hint: String,
    initial: String,
    suffix: String,
    onOk: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalThemeSpec.current
    var text by remember { mutableStateOf(initial) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(s.cardCorner))
                .background(s.cardBg)
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Text(title, color = s.textPrimary, fontSize = 19.sp, fontFamily = s.fontFamily)
            Text(
                hint,
                color = s.textDim,
                fontSize = 13.sp,
                fontFamily = s.fontFamily,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(7) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = s.textPrimary, fontSize = 28.sp),
                    cursorBrush = SolidColor(s.accent),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
                Text(
                    "  $suffix",
                    color = s.textSecondary,
                    fontSize = 18.sp,
                    fontFamily = s.fontFamily
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.09f))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 22.dp, vertical = 11.dp)
                ) {
                    Text("Отмена", color = s.textSecondary, fontSize = 15.sp, fontFamily = s.fontFamily)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(s.accent)
                        .clickable { onOk(text.toIntOrNull() ?: 0) }
                        .padding(horizontal = 26.dp, vertical = 11.dp)
                ) {
                    Text("Сохранить", color = s.onAccent, fontSize = 15.sp, fontFamily = s.fontFamily)
                }
            }
        }
    }
}
