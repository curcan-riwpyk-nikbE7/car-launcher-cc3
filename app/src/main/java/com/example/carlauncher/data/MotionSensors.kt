package com.example.carlauncher.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Датчики движения головного устройства.
 *
 * Зачем: у штатного CC3 машинка на экране наклоняется от настоящего
 * гироскопа LSM6DS3. У большинства ГУ гироскопа нет, но простой
 * акселерометр стоит часто — его хватит, чтобы поймать крен в повороте
 * и клевок при торможении.
 *
 * Что именно есть на конкретной магнитоле, заранее неизвестно, поэтому
 * сначала опрашиваем систему, а уже потом решаем, как анимировать.
 */

/** Наклон кузова, нормализованный в -1..1. */
data class Tilt(
    /** Крен: <0 — валит влево, >0 — вправо. */
    val roll: Float = 0f,
    /** Продольный: <0 — клевок носом (торможение), >0 — присед (разгон). */
    val pitch: Float = 0f,
    /** Есть ли вообще датчик. false — анимируем только от GPS-скорости. */
    val available: Boolean = false
)

object MotionSensors {

    /** Человеческий список датчиков — показываем в настройках. */
    fun describe(context: Context): List<String> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return listOf("Датчики недоступны")

        val checks = listOf(
            Sensor.TYPE_ACCELEROMETER to "Акселерометр",
            Sensor.TYPE_GYROSCOPE to "Гироскоп",
            Sensor.TYPE_ROTATION_VECTOR to "Вектор поворота",
            Sensor.TYPE_MAGNETIC_FIELD to "Компас",
            Sensor.TYPE_LIGHT to "Датчик света"
        )

        return checks.map { (type, name) ->
            val s = sm.getDefaultSensor(type)
            if (s != null) "$name: есть (${s.name})" else "$name: нет"
        }
    }

    fun hasAccelerometer(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        return sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }
}

/**
 * Наклон кузова по акселерометру.
 *
 * Показания сильно шумят от вибрации двигателя и неровностей, поэтому
 * гоняем через простой фильтр: новое значение подмешиваем к старому
 * малой долей. Без этого машинка на экране тряслась бы мелкой дрожью.
 */
@Composable
fun rememberTilt(enabled: Boolean = true): State<Tilt> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Tilt()) }

    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }

        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || sensor == null) {
            state.value = Tilt(available = false)
            return@DisposableEffect onDispose { }
        }

        var roll = 0f
        var pitch = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // Боковое и продольное ускорение в м/с². 4 м/с² — уже
                // заметный манёвр, поэтому нормируем по нему.
                val rawRoll = (e.values[0] / 4f).coerceIn(-1f, 1f)
                val rawPitch = (e.values[1] / 4f).coerceIn(-1f, 1f)

                // Сглаживание: 12% нового значения за раз
                roll += (rawRoll - roll) * 0.12f
                pitch += (rawPitch - pitch) * 0.12f

                state.value = Tilt(roll = roll, pitch = pitch, available = true)
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }

        // SENSOR_DELAY_GAME — примерно 50 Гц. Чаще не нужно: экран
        // всё равно обновляется 60 раз в секунду, а батарею ГУ
        // экономить незачем, но греть процессор лишний раз не стоит.
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    return state
}
