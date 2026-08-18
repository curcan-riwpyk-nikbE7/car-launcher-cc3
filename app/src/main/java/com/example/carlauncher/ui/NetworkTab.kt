package com.example.carlauncher.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.NetworkScanner
import com.example.carlauncher.data.QuickControls
import kotlinx.coroutines.delay

/**
 * Вкладка «Сеть»: Wi-Fi слева, Bluetooth справа.
 *
 * Раньше сеть жила плиткой внутри «Системы» и открывала диалог поверх
 * списка — тот вставал в поток колонки и налезал на соседние плитки.
 * Плюс любое действие всё равно заканчивалось системным экраном:
 * чужой интерфейс с мелкими строками, в которые на ходу не попасть,
 * и возврат непонятно куда.
 *
 * Теперь это полноценный раздел рядом с остальными. Поиск сетей,
 * ввод пароля и сопряжение — всё внутри лаунчера.
 */
@Composable
fun NetworkTab() {
    val s = LocalThemeSpec.current

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        WifiPanel(modifier = Modifier.weight(1f).fillMaxHeight())
        BluetoothPanel(modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

// ─────────────────────────────── Wi-Fi ───────────────────────────────

@Composable
private fun WifiPanel(modifier: Modifier = Modifier) {
    val s = LocalThemeSpec.current
    val context = LocalContext.current

    var revision by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var askPassword by remember { mutableStateOf<NetworkScanner.WifiNet?>(null) }

    val on = remember(revision) { NetworkScanner.isWifiOn(context) }
    val nets = remember(revision) { NetworkScanner.scanResults(context) }
    val current = remember(revision) { NetworkScanner.currentSsid(context) }

    // Результаты поиска приходят broadcast-ом, а не возвратом из startScan
    DisposableEffect(Unit) {
        val r = NetworkScanner.registerScanReceiver(context) {
            scanning = false
            revision++
        }
        onDispose { runCatching { context.unregisterReceiver(r) } }
    }

    // Первый поиск сразу при открытии: ждать нажатия незачем
    LaunchedEffect(on) {
        if (on) {
            scanning = NetworkScanner.startScan(context)
            // Если система не пришлёт результат, снимаем индикатор сами:
            // иначе крутилка висела бы вечно
            delay(12_000)
            scanning = false
            revision++
        }
    }

    Column(modifier = modifier) {
        SectionHeader(
            icon = Icons.Rounded.Wifi,
            title = "Wi-Fi",
            subtitle = when {
                !on -> "выключен"
                current != null -> "подключён: $current"
                else -> "не подключён"
            },
            enabled = on,
            busy = scanning,
            onToggle = {
                if (!QuickControls.toggleWifi(context)) {
                    QuickControls.openInternetPanel(context)
                } else revision++
            },
            onRefresh = {
                scanning = NetworkScanner.startScan(context)
                revision++
            }
        )

        if (!on) {
            EmptyHint("Включите Wi-Fi, чтобы увидеть сети")
            return@Column
        }
        if (nets.isEmpty()) {
            EmptyHint(if (scanning) "Ищу сети…" else "Сети не найдены")
            return@Column
        }

        LazyColumn(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(nets, key = { it.ssid }) { net ->
                NetRow(
                    title = net.ssid,
                    subtitle = when {
                        net.connected -> "подключено"
                        net.secured -> "защищённая"
                        else -> "открытая"
                    },
                    active = net.connected,
                    trailing = {
                        if (net.secured) {
                            Icon(
                                Icons.Rounded.Lock, null,
                                tint = s.textDim,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        SignalBars(net.level)
                    },
                    onClick = {
                        when {
                            net.connected -> Unit
                            net.secured -> askPassword = net
                            else -> {
                                if (!NetworkScanner.connect(context, net.ssid, "")) {
                                    QuickControls.openInternetPanel(context)
                                }
                                revision++
                            }
                        }
                    }
                )
            }
        }
    }

    askPassword?.let { net ->
        PasswordDialog(
            ssid = net.ssid,
            onDismiss = { askPassword = null },
            onConnect = { pass ->
                // Прямое подключение доступно только сборке с подписью
                // прошивки: обычным приложениям Android с версии 10
                // показывает свой диалог выбора сети.
                if (!NetworkScanner.connect(context, net.ssid, pass)) {
                    QuickControls.openInternetPanel(context)
                }
                askPassword = null
                revision++
            }
        )
    }
}

/** Палочки уровня сигнала. Рисуем сами: готовых иконок на 5 делений нет. */
@Composable
private fun SignalBars(level: Int) {
    val s = LocalThemeSpec.current
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        repeat(4) { i ->
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = (5 + i * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < level) s.accent else s.textDim.copy(alpha = 0.3f))
            )
        }
    }
}

// ───────────────────────────── Bluetooth ─────────────────────────────

@Composable
private fun BluetoothPanel(modifier: Modifier = Modifier) {
    val s = LocalThemeSpec.current
    val context = LocalContext.current

    var revision by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<BluetoothDevice>() }

    val on = remember(revision) { QuickControls.isBluetoothOn(context) }
    val devices = remember(revision, found.size) {
        NetworkScanner.btDevices(context, found.toList())
    }

    DisposableEffect(Unit) {
        val r = NetworkScanner.registerBtReceiver(
            context,
            onFound = { d -> if (found.none { it.address == d.address }) found.add(d) },
            onChanged = { revision++ }
        )
        onDispose {
            NetworkScanner.stopBtScan(context)
            runCatching { context.unregisterReceiver(r) }
        }
    }

    Column(modifier = modifier) {
        SectionHeader(
            icon = Icons.Rounded.Bluetooth,
            title = "Bluetooth",
            subtitle = if (on) "${devices.count { it.bonded }} сопряжено" else "выключен",
            enabled = on,
            busy = scanning,
            onToggle = {
                if (!QuickControls.toggleBluetooth(context)) {
                    QuickControls.openBluetoothSettings(context)
                } else revision++
            },
            onRefresh = {
                found.clear()
                scanning = NetworkScanner.startBtScan(context)
                revision++
            }
        )

        if (!on) {
            EmptyHint("Включите Bluetooth, чтобы найти телефон")
            return@Column
        }
        if (devices.isEmpty()) {
            EmptyHint(if (scanning) "Ищу устройства…" else "Нажмите обновить для поиска")
            return@Column
        }

        LazyColumn(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(devices, key = { it.address }) { d ->
                NetRow(
                    title = d.name,
                    subtitle = when {
                        d.connected -> "подключено"
                        d.bonded -> "сопряжено"
                        else -> "новое устройство"
                    },
                    active = d.connected,
                    trailing = {},
                    onClick = {
                        NetworkScanner.pairOrConnect(context, d.address)
                        revision++
                    }
                )
            }
        }
    }
}

// ─────────────────────────── общие элементы ───────────────────────────

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit
) {
    val s = LocalThemeSpec.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(s.cardBg)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            tint = if (enabled) s.accent else s.textDim,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = s.textPrimary, fontSize = 17.sp, fontFamily = s.fontFamily)
            Text(subtitle, color = s.textDim, fontSize = 12.sp, fontFamily = s.fontFamily)
        }
        if (enabled) {
            Icon(
                Icons.Rounded.Refresh, "Обновить",
                tint = if (busy) s.accent else s.textSecondary,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable(onClick = onRefresh)
                    .padding(9.dp)
            )
            Spacer(Modifier.size(10.dp))
        }
        ThemedSwitch(enabled) { onToggle() }
    }
}

@Composable
private fun NetRow(
    title: String,
    subtitle: String,
    active: Boolean,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val s = LocalThemeSpec.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) s.accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (active) s.accent else s.textPrimary,
                fontSize = 15.sp,
                fontFamily = s.fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, color = s.textDim, fontSize = 11.sp, fontFamily = s.fontFamily)
        }
        if (active) {
            Icon(
                Icons.Rounded.Check, null,
                tint = s.accent,
                modifier = Modifier.size(18.dp)
            )
        }
        trailing()
    }
}

@Composable
private fun EmptyHint(text: String) {
    val s = LocalThemeSpec.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = s.textDim, fontSize = 14.sp, fontFamily = s.fontFamily)
    }
}

/** Ввод пароля сети. Экранная клавиатура на ГУ есть, годится. */
@Composable
private fun PasswordDialog(
    ssid: String,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit
) {
    val s = LocalThemeSpec.current
    var pass by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .clip(RoundedCornerShape(s.cardCorner))
                .background(s.bg.first())
                .clickable(enabled = false) {}
                .padding(22.dp)
        ) {
            Text(ssid, color = s.textPrimary, fontSize = 18.sp, fontFamily = s.fontFamily)
            Text(
                "Введите пароль сети",
                color = s.textDim,
                fontSize = 12.sp,
                fontFamily = s.fontFamily,
                modifier = Modifier.padding(top = 3.dp, bottom = 12.dp)
            )
            BasicTextField(
                value = pass,
                onValueChange = { pass = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = TextStyle(color = s.textPrimary, fontSize = 18.sp),
                cursorBrush = SolidColor(s.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 16.dp)
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
                        .clickable { onConnect(pass) }
                        .padding(horizontal = 26.dp, vertical = 11.dp)
                ) {
                    Text("Подключить", color = s.onAccent, fontSize = 15.sp, fontFamily = s.fontFamily)
                }
            }
        }
    }
}
