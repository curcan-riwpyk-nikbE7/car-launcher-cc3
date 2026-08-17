package com.example.carlauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * Питание автомобиля: зажигание и напряжение бортовой сети.
 *
 * Почему не BatteryManager: у головного устройства своей батареи нет,
 * системный API отдаёт фиктивные 100% — к состоянию автомобильного
 * аккумулятора это отношения не имеет.
 *
 * Данные идут от MCU через сервис производителя. В прошивке этого ГУ
 * (Reglink) нашлись com.reglink.action.acc_state_changed — рассылается
 * наружу, и getVoltage — 53 упоминания, но только внутри их сервиса
 * через AIDL, снаружи не дотянуться.
 *
 * Поэтому напряжение добывается тремя путями сразу, от надёжного
 * к сомнительному: broadcast от MCU, системные свойства, файлы sysfs.
 * Если молчат все три — вольты не показываются вовсе: выдуманное
 * число хуже пустого места.
 */
object CarPower {

    private const val TAG = "CarPower"

    /**
     * Действия, которыми ГУ сообщают о смене питания.
     *
     * Первые четыре вычитаны прямо из образа /system этой прошивки,
     * остальные — запасные для других головных устройств. Раньше
     * список был собран по догадкам, и половина имён не существовала.
     *
     * DroidCarService — центральный сервис Reglink, через который
     * проходят данные от MCU. Он рассылает наружу и старт, и готовность,
     * поэтому слушаем оба: после инициализации значения уже доступны.
     */
    private val ACC_ACTIONS = listOf(
        "com.reglink.action.acc_state_changed",
        "com.reglink.action.DroidCarServiceInitCompleted",
        "com.reglink.action.DroidCarServiceStarted",
        "com.reglink.action.DroidCarService",
        "com.reglink.action.ACC_STATE_CHANGED",
        "com.reglink.action.VOLTAGE_CHANGED",
        "android.intent.action.ACC_STATE_CHANGED",
        "com.hzbhd.acc.state",
        "com.microntek.ACC_STATE",
        "com.syu.ACC_STATE"
    )

    /** Поля, в которых разные прошивки передают напряжение. */
    private val VOLT_KEYS = listOf(
        "voltage", "volt", "batteryVoltage", "battery_voltage",
        "acc_voltage", "EXTRA_VOLTAGE", "extra_voltage",
        "vol", "value", "data"
    )

    private val ACC_KEYS = listOf("acc", "state", "acc_state", "value", "on", "status")

    /**
     * Состояние питания.
     *
     * @param accOn включено ли зажигание
     * @param volts напряжение или -1, если неизвестно
     * @param source откуда получено — для экрана диагностики
     */
    data class Power(
        val accOn: Boolean = true,
        val volts: Float = -1f,
        val source: String = "нет данных"
    ) {
        val hasVoltage: Boolean get() = volts > 0f

        /**
         * Оценка состояния. Пороги для свинцового аккумулятора 12 В:
         * при работающем двигателе генератор держит 13.5-14.5 В,
         * на заглушенном 12.6 В — полный заряд, 11.8 В — уже разряжен.
         */
        val level: Level
            get() = when {
                volts <= 0f -> Level.Unknown
                volts >= 13.2f -> Level.Charging   // генератор работает
                volts >= 12.4f -> Level.Good
                volts >= 11.8f -> Level.Low
                else -> Level.Critical
            }
    }

    enum class Level { Unknown, Critical, Low, Good, Charging }

    /**
     * Подписка на состояние питания.
     *
     * Broadcast приходит редко и не на всех прошивках, поэтому
     * параллельно раз в 30 секунд опрашиваем системные свойства
     * и sysfs. Опрос дешёвый — чтение короткой строки.
     */
    @Composable
    fun rememberPower(): State<Power> {
        val context = androidx.compose.ui.platform.LocalContext.current
        val state = remember { mutableStateOf(Power()) }

        DisposableEffect(Unit) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    val ex = i?.extras ?: return
                    val acc = readAcc(ex)
                    val volt = readVoltage(ex)

                    state.value = state.value.copy(
                        accOn = acc ?: state.value.accOn,
                        volts = if (volt > 0f) volt else state.value.volts,
                        source = if (volt > 0f) "MCU" else state.value.source
                    )
                }
            }
            val f = IntentFilter().apply { ACC_ACTIONS.forEach { addAction(it) } }
            // Exported-флаг нужен с Android 13: без него система
            // отклоняет регистрацию приёмника чужих broadcast.
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    context.registerReceiver(r, f, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(r, f)
                }
            }.onFailure { Log.w(TAG, "Приёмник ACC не зарегистрировался", it) }

            onDispose { runCatching { context.unregisterReceiver(r) } }
        }

        // Запасной опрос: broadcast может не прийти ни разу.
        LaunchedEffect(Unit) {
            while (true) {
                if (state.value.source != "MCU") {
                    val (v, src) = pollVoltage()
                    if (v > 0f) {
                        state.value = state.value.copy(volts = v, source = src)
                    }
                }
                delay(30_000)
            }
        }

        return state
    }

    private fun readAcc(ex: Bundle): Boolean? {
        for (key in ACC_KEYS) {
            if (!ex.containsKey(key)) continue
            @Suppress("DEPRECATION")
            val v = ex.get(key)
            val r = when (v) {
                is Boolean -> v
                is Int -> v != 0
                is String -> v == "1" || v.equals("true", true)
                else -> null
            }
            if (r != null) return r
        }
        return null
    }

    private fun readVoltage(ex: Bundle): Float {
        for (key in VOLT_KEYS) {
            if (!ex.containsKey(key)) continue
            @Suppress("DEPRECATION")
            val raw = when (val v = ex.get(key)) {
                is Float -> v
                is Double -> v.toFloat()
                is Int -> v.toFloat()
                is String -> v.toFloatOrNull() ?: continue
                else -> continue
            }
            val volt = normalize(raw)
            if (volt > 0f) return volt
        }
        return -1f
    }

    /**
     * Приводит к вольтам.
     *
     * Прошивки шлют одно и то же тремя способами: 12.6 вольтами,
     * 126 десятыми или 1260 сотыми. Различаем по величине —
     * осмысленный диапазон бортовой сети узкий, перепутать не с чем.
     */
    private fun normalize(raw: Float): Float {
        val v = when {
            raw > 1000f -> raw / 100f
            raw > 100f -> raw / 10f
            else -> raw
        }
        // 6..32 В покрывает и 12-вольтовые легковые, и 24-вольтовые
        // грузовые. Всё, что вне — мусор из чужого поля.
        return if (v in 6f..32f) v else -1f
    }

    /**
     * Опрос без broadcast: системные свойства, затем sysfs.
     *
     * @return напряжение и источник
     */
    fun pollVoltage(): Pair<Float, String> {
        voltageFromProps().let { if (it > 0f) return it to "свойства" }
        voltageFromSysfs().let { if (it > 0f) return it to "sysfs" }
        return -1f to "нет данных"
    }

    /**
     * Напряжение из системных свойств.
     *
     * carinfo.BatteryVoltage и persist.acc.signal.status найдены в образе
     * прошивки — это настоящие имена, которыми пользуется сервис Reglink.
     * Остальные оставлены запасными: у других ГУ имена свои.
     */
    fun voltageFromProps(): Float = runCatching {
        val cls = Class.forName("android.os.SystemProperties")
        val get = cls.getMethod("get", String::class.java, String::class.java)
        for (key in listOf(
            "carinfo.BatteryVoltage",
            "persist.acc.signal.status",
            "persist.sys.car.voltage", "sys.car.voltage",
            "persist.reglink.voltage", "sys.reglink.voltage",
            "persist.sys.acc.voltage", "persist.vendor.voltage",
            "sys.mcu.voltage", "persist.sys.mcu.voltage"
        )) {
            val s = (get.invoke(null, key, "") as? String) ?: continue
            val v = s.toFloatOrNull() ?: continue
            val volt = normalize(v)
            if (volt > 0f) return volt
        }
        -1f
    }.getOrDefault(-1f)

    /**
     * Напряжение из sysfs.
     *
     * На части ГУ драйвер MCU выкладывает значение обычным файлом.
     * Читаем без прав root — файлы открыты на чтение всем.
     */
    private fun voltageFromSysfs(): Float = runCatching {
        for (path in listOf(
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/ac/voltage_now",
            "/sys/devices/platform/mcu/voltage",
            "/sys/class/mcu/voltage",
            "/proc/mcu_voltage"
        )) {
            val f = java.io.File(path)
            if (!f.canRead()) continue
            val raw = f.readText().trim().toFloatOrNull() ?: continue
            // voltage_now отдаёт микровольты — 12600000 это 12.6 В
            val v = if (raw > 100_000f) raw / 1_000_000f else raw
            val volt = normalize(v)
            if (volt > 0f) return volt
        }
        -1f
    }.getOrDefault(-1f)
}
