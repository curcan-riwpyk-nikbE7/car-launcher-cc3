package com.example.carlauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Питание автомобиля: зажигание и напряжение бортовой сети.
 *
 * Почему не BatteryManager: у головного устройства своей батареи нет,
 * и системный API отдаёт фиктивные 100% — к состоянию автомобильного
 * аккумулятора это отношения не имеет.
 *
 * Данные приходят от MCU через сервис производителя. В прошивке этого
 * ГУ (Reglink) нашлись:
 *   com.reglink.action.acc_state_changed   — рассылается наружу
 *   getVoltage                             — есть, но только внутри
 *                                            их сервиса, через AIDL
 *
 * Поэтому зажигание читаем надёжно, а напряжение — как получится:
 * перебираем известные имена полей в том же broadcast и системные
 * свойства. Если ничего не пришло, вольты просто не показываем:
 * выдуманное число хуже пустого места.
 */
object CarPower {

    /** Известные действия, которыми ГУ сообщают о смене ACC. */
    private val ACC_ACTIONS = listOf(
        "com.reglink.action.acc_state_changed",
        "com.reglink.action.ACC_STATE_CHANGED",
        "android.intent.action.ACC_STATE_CHANGED",
        "com.hzbhd.acc.state",
        "com.microntek.ACC_STATE"
    )

    /** Поля, в которых разные прошивки передают напряжение. */
    private val VOLT_KEYS = listOf(
        "voltage", "volt", "batteryVoltage", "acc_voltage",
        "EXTRA_VOLTAGE", "value", "data"
    )

    private val ACC_KEYS = listOf("acc", "state", "acc_state", "value", "on")

    /**
     * Подписка на состояние питания.
     *
     * @return пара «зажигание включено» и «напряжение», где напряжение
     *   отрицательное, если прошивка его не передаёт.
     */
    @Composable
    fun rememberPower(): State<Pair<Boolean, Float>> {
        val context = androidx.compose.ui.platform.LocalContext.current
        val state = remember { mutableStateOf(true to -1f) }

        DisposableEffect(Unit) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    val ex = i?.extras ?: return

                    // Зажигание: разные прошивки шлют то boolean, то int
                    var acc: Boolean? = null
                    for (key in ACC_KEYS) {
                        if (!ex.containsKey(key)) continue
                        acc = when (val v = ex.get(key)) {
                            is Boolean -> v
                            is Int -> v != 0
                            is String -> v == "1" || v.equals("true", true)
                            else -> null
                        }
                        if (acc != null) break
                    }

                    // Напряжение приходит либо вольтами (12.6), либо
                    // сотыми (1260) — приводим к вольтам по величине.
                    var volt = -1f
                    for (key in VOLT_KEYS) {
                        if (!ex.containsKey(key)) continue
                        val raw = when (val v = ex.get(key)) {
                            is Float -> v
                            is Double -> v.toFloat()
                            is Int -> v.toFloat()
                            is String -> v.toFloatOrNull() ?: continue
                            else -> continue
                        }
                        volt = when {
                            raw > 1000f -> raw / 100f   // 1260 -> 12.6
                            raw > 100f -> raw / 10f     // 126  -> 12.6
                            else -> raw
                        }
                        if (volt in 6f..32f) break else volt = -1f
                    }

                    state.value = (acc ?: state.value.first) to
                        (if (volt > 0f) volt else state.value.second)
                }
            }
            val f = IntentFilter().apply { ACC_ACTIONS.forEach { addAction(it) } }
            runCatching { context.registerReceiver(r, f) }
            onDispose { runCatching { context.unregisterReceiver(r) } }
        }

        return state
    }

    /**
     * Напряжение из системных свойств — запасной путь, если broadcast
     * молчит. Часть прошивок кладёт его туда, имена у всех свои.
     */
    fun voltageFromProps(): Float = runCatching {
        val cls = Class.forName("android.os.SystemProperties")
        val get = cls.getMethod("get", String::class.java, String::class.java)
        for (key in listOf(
            "persist.sys.car.voltage", "sys.car.voltage",
            "persist.reglink.voltage", "persist.sys.acc.voltage",
            "persist.vendor.voltage"
        )) {
            val v = (get.invoke(null, key, "") as? String)?.toFloatOrNull() ?: continue
            val volt = if (v > 100f) v / 10f else v
            if (volt in 6f..32f) return volt
        }
        -1f
    }.getOrDefault(-1f)
}
