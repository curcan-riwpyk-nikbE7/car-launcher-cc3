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

    // ────────────────────────── Мобильный интернет ──────────────────────────

    /** Что показывать про сотовую связь. */
    data class MobileInfo(
        val hasSim: Boolean,
        val operator: String,
        val network: String,   // 4G, 3G, LTE…
        val enabled: Boolean
    )

    /**
     * Состояние сотовой сети.
     *
     * На головных устройствах SIM-карты часто нет вовсе — тогда блок
     * не нужно показывать совсем, вместо того чтобы рисовать пустые
     * прочерки.
     */
    @SuppressLint("MissingPermission")
    fun mobileInfo(context: Context): MobileInfo = runCatching {
        val tm = context.applicationContext
            .getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager

        val simReady = tm.simState == android.telephony.TelephonyManager.SIM_STATE_READY
        val op = tm.networkOperatorName?.takeIf { it.isNotBlank() } ?: "нет оператора"

        // Тип сети читаем через dataNetworkType: getNetworkType устарел
        // и на Android 11+ бросает исключение без разрешения.
        val type = runCatching {
            if (Build.VERSION.SDK_INT >= 30) tm.dataNetworkType else @Suppress("DEPRECATION") tm.networkType
        }.getOrDefault(0)

        MobileInfo(
            hasSim = simReady,
            operator = op,
            network = networkTypeName(type),
            enabled = QuickControls.isMobileDataOn(context)
        )
    }.getOrDefault(MobileInfo(false, "нет SIM-карты", "", false))

    private fun networkTypeName(type: Int): String = when (type) {
        20 -> "5G"
        13, 19 -> "4G LTE"
        3, 8, 9, 10, 15 -> "3G"
        1, 2, 16 -> "2G"
        else -> ""
    }

    // ─────────────────────────── Раздача интернета ───────────────────────────

    data class HotspotInfo(
        val active: Boolean,
        val ssid: String,
        val clients: Int
    )

    /**
     * Точка доступа.
     *
     * Публичного API нет ни для чтения состояния, ни для включения:
     * всё через скрытые методы WifiManager. На части прошивок они
     * закрыты — тогда возвращаем «недоступно» и уводим в системные
     * настройки, а не притворяемся, что работает.
     */
    fun hotspotInfo(context: Context): HotspotInfo = runCatching {
        val wm = wifi(context)
        val m = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
        m.isAccessible = true
        val on = m.invoke(wm) as? Boolean == true

        val ssid = runCatching {
            val cfg = wm.javaClass.getDeclaredMethod("getWifiApConfiguration").apply {
                isAccessible = true
            }.invoke(wm)
            @Suppress("DEPRECATION")
            (cfg as? WifiConfiguration)?.SSID ?: ""
        }.getOrDefault("")

        HotspotInfo(active = on, ssid = ssid, clients = 0)
    }.getOrDefault(HotspotInfo(false, "", 0))

    /**
     * Включение раздачи.
     *
     * setWifiApEnabled удалён из публичного API в Android 8, а замена
     * (startTethering) требует прав системы. Пробуем оба пути.
     *
     * @return false — значит остаётся системный экран
     */
    @SuppressLint("PrivateApi")
    fun toggleHotspot(context: Context): Boolean = runCatching {
        val wm = wifi(context)
        val target = !hotspotInfo(context).active

        // Путь для старых прошивок: метод есть до Android 8,
        // а на китайских ГУ его часто оставляют и дальше.
        runCatching {
            @Suppress("DEPRECATION")
            val m = wm.javaClass.getDeclaredMethod(
                "setWifiApEnabled",
                WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType
            )
            m.isAccessible = true
            m.invoke(wm, null, target)
            return hotspotInfo(context).active == target
        }

        false
    }.getOrElse {
        Log.w(TAG, "Раздачу переключить не удалось", it)
        false
    }

    fun openHotspotSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent().setClassName(
                    "com.android.settings",
                    "com.android.settings.TetherSettings"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    // ───────────────────────────── Bluetooth ─────────────────────────────

    data class BtItem(
        val name: String,
        val address: String,
        val bonded: Boolean,
        val connected: Boolean,
        /** Что именно работает: музыка, звонки. Пусто — просто сопряжено. */
        val profiles: String = "",
        /** Заряд телефона, -1 если не передаёт. */
        val battery: Int = -1
    )

    /**
     * Чем занято устройство: музыка, звонки или и то и другое.
     *
     * Состояние профиля спрашиваем у адаптера, а не у устройства:
     * getProfileConnectionState отвечает сразу, тогда как обычный путь
     * через getProfileProxy асинхронный и к моменту отрисовки списка
     * ещё не готов.
     *
     * Метод говорит лишь «есть ли хоть одно подключение по профилю»,
     * без привязки к конкретному устройству. Для магнитолы этого
     * достаточно: телефон подключается ровно один.
     */
    @SuppressLint("MissingPermission")
    private fun activeProfiles(context: Context): String {
        val a = adapter(context) ?: return ""
        val out = mutableListOf<String>()
        runCatching {
            if (a.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP) ==
                android.bluetooth.BluetoothProfile.STATE_CONNECTED
            ) out += "музыка"
            if (a.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET) ==
                android.bluetooth.BluetoothProfile.STATE_CONNECTED
            ) out += "звонки"
        }
        return out.joinToString(" · ")
    }

    /**
     * Заряд телефона по Bluetooth.
     *
     * Уровень приходит по профилю HFP и лежит в скрытом методе
     * getBatteryLevel — публичного способа его узнать нет.
     */
    @SuppressLint("MissingPermission")
    private fun batteryOf(device: BluetoothDevice): Int = runCatching {
        val m = device.javaClass.getMethod("getBatteryLevel")
        (m.invoke(device) as? Int)?.takeIf { it in 0..100 } ?: -1
    }.getOrDefault(-1)

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
            val profiles = activeProfiles(context)
            all.map { d ->
                val isConnected = runCatching {
                    d.javaClass.getMethod("isConnected").invoke(d) as? Boolean == true
                }.getOrDefault(false)
                BtItem(
                    name = runCatching { d.name }.getOrNull() ?: d.address,
                    address = d.address,
                    bonded = d.bondState == BluetoothDevice.BOND_BONDED,
                    connected = isConnected,
                    // Профили показываем только у подключённого: у остальных
                    // они всё равно пусты, а строка сбивала бы с толку.
                    profiles = if (isConnected) profiles else "",
                    battery = if (isConnected) batteryOf(d) else -1
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
