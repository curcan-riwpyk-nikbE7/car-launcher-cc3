package com.example.carlauncher.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

/**
 * Поиск и подключение сетей — Wi-Fi и Bluetooth.
 *
 * Раньше лаунчер умел только показать состояние и отправить человека
 * в системные настройки. Это чужой интерфейс поверх нашего, с мелкими
 * строками, в которые на ходу не попасть, и с возвратом непонятно куда.
 *
 * Здесь всё своё: список сетей, уровень сигнала, подключение по паролю.
 * Часть операций Android с версии 10 закрыл для обычных приложений —
 * такие честно возвращают false, и тогда открывается системный экран.
 * Сборке с подписью прошивки система доверяет как своей, и там всё
 * работает напрямую.
 */
object NetworkScanner {

    private const val TAG = "NetworkScanner"

    // ─────────────────────────────── Wi-Fi ───────────────────────────────

    /** Найденная сеть. */
    data class WifiNet(
        val ssid: String,
        val level: Int,          // 0..4 палочки
        val secured: Boolean,
        val connected: Boolean
    )

    private fun wifi(context: Context) =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun isWifiOn(context: Context): Boolean =
        runCatching { wifi(context).isWifiEnabled }.getOrDefault(false)

    /**
     * Имя текущей сети.
     *
     * SSID приходит в кавычках, а если разрешения на геолокацию нет —
     * строкой <unknown ssid>. И то и другое показывать нельзя.
     */
    @SuppressLint("MissingPermission")
    fun currentSsid(context: Context): String? = runCatching {
        @Suppress("DEPRECATION")
        val info = wifi(context).connectionInfo ?: return null
        val raw = info.ssid?.trim('"') ?: return null
        if (raw.isBlank() || raw.contains("unknown", true)) null else raw
    }.getOrNull()

    /** Запускает поиск сетей. Результат придёт broadcast-ом. */
    @SuppressLint("MissingPermission")
    fun startScan(context: Context): Boolean = runCatching {
        @Suppress("DEPRECATION")
        wifi(context).startScan()
    }.getOrDefault(false)

    /**
     * Список найденных сетей.
     *
     * Точки с одинаковым именем схлопываем: у роутеров с двумя
     * диапазонами SSID один, и в списке он двоился бы. Оставляем
     * тот, у которого сигнал сильнее.
     */
    @SuppressLint("MissingPermission")
    fun scanResults(context: Context): List<WifiNet> = runCatching {
        val cur = currentSsid(context)
        wifi(context).scanResults
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (ssid, list) ->
                val best = list.maxByOrNull { it.level } ?: list.first()
                WifiNet(
                    ssid = ssid,
                    level = WifiManager.calculateSignalLevel(best.level, 5),
                    secured = isSecured(best),
                    connected = ssid == cur
                )
            }
            .sortedWith(compareByDescending<WifiNet> { it.connected }.thenByDescending { it.level })
    }.getOrDefault(emptyList())

    private fun isSecured(r: ScanResult): Boolean {
        val caps = r.capabilities ?: return false
        return caps.contains("WEP") || caps.contains("PSK") ||
            caps.contains("EAP") || caps.contains("SAE")
    }

    /**
     * Подключение к сети по паролю.
     *
     * Старый способ через WifiConfiguration с Android 10 не работает:
     * addNetwork возвращает -1 для обычных приложений. Оставлен ради
     * сборки с системными правами — там он единственный, что позволяет
     * подключиться без диалога.
     *
     * @return false, если система отказала — тогда остаётся системный
     *   выбор сети
     */
    @SuppressLint("MissingPermission")
    fun connect(context: Context, ssid: String, password: String): Boolean = runCatching {
        val wm = wifi(context)

        @Suppress("DEPRECATION")
        val conf = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            if (password.isBlank()) {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            } else {
                preSharedKey = "\"$password\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            }
        }

        @Suppress("DEPRECATION")
        val id = wm.addNetwork(conf)
        if (id < 0) return false

        @Suppress("DEPRECATION")
        wm.disconnect()
        @Suppress("DEPRECATION")
        val ok = wm.enableNetwork(id, true)
        @Suppress("DEPRECATION")
        wm.reconnect()
        ok
    }.getOrElse {
        Log.w(TAG, "Подключение к $ssid не удалось", it)
        false
    }

    /** Слушатель результатов поиска. */
    fun registerScanReceiver(context: Context, onResult: () -> Unit): BroadcastReceiver {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) = onResult()
        }
        val f = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(r, f, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(r, f)
            }
        }
        return r
    }

    // ───────────────────────────── Bluetooth ─────────────────────────────

    data class BtItem(
        val name: String,
        val address: String,
        val bonded: Boolean,
        val connected: Boolean
    )

    private fun adapter(context: Context): BluetoothAdapter? = runCatching {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
    }.getOrNull()

    /**
     * Известные и найденные устройства.
     *
     * Сопряжённые показываем всегда, даже когда поиск не запускался:
     * телефон обычно уже в списке, и человеку нужно просто нажать
     * на него, а не ждать сканирования.
     */
    @SuppressLint("MissingPermission")
    fun btDevices(context: Context, found: List<BluetoothDevice> = emptyList()): List<BtItem> =
        runCatching {
            val a = adapter(context) ?: return emptyList()
            val bonded = a.bondedDevices ?: emptySet()
            val all = (bonded + found).distinctBy { it.address }
            all.map { d ->
                BtItem(
                    name = runCatching { d.name }.getOrNull() ?: d.address,
                    address = d.address,
                    bonded = d.bondState == BluetoothDevice.BOND_BONDED,
                    connected = runCatching {
                        d.javaClass.getMethod("isConnected").invoke(d) as? Boolean == true
                    }.getOrDefault(false)
                )
            }.sortedWith(
                compareByDescending<BtItem> { it.connected }.thenByDescending { it.bonded }
            )
        }.getOrDefault(emptyList())

    @SuppressLint("MissingPermission")
    fun startBtScan(context: Context): Boolean = runCatching {
        val a = adapter(context) ?: return false
        if (a.isDiscovering) a.cancelDiscovery()
        a.startDiscovery()
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun stopBtScan(context: Context) {
        runCatching { adapter(context)?.cancelDiscovery() }
    }

    /**
     * Сопряжение. Если устройство уже известно — пробуем подключить
     * профиль A2DP, иначе запускаем создание пары.
     */
    @SuppressLint("MissingPermission")
    fun pairOrConnect(context: Context, address: String): Boolean = runCatching {
        val a = adapter(context) ?: return false
        val d = a.getRemoteDevice(address) ?: return false
        if (d.bondState != BluetoothDevice.BOND_BONDED) {
            d.createBond()
        } else {
            // Публичного способа подключить конкретный профиль нет,
            // но у BluetoothDevice есть скрытый connect() — он есть
            // во всех версиях и на китайских прошивках работает.
            runCatching {
                d.javaClass.getMethod("connect").invoke(d)
                true
            }.getOrDefault(false)
        }
    }.getOrDefault(false)

    /** Слушатель найденных устройств. */
    fun registerBtReceiver(
        context: Context,
        onFound: (BluetoothDevice) -> Unit,
        onChanged: () -> Unit
    ): BroadcastReceiver {
        val r = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(c: Context?, i: Intent?) {
                when (i?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        @Suppress("DEPRECATION")
                        val d: BluetoothDevice? = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        d?.let(onFound)
                    }
                    else -> onChanged()
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(r, f, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(r, f)
            }
        }
        return r
    }
}
