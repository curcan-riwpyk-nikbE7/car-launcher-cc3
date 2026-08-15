package com.example.carlauncher.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context

/**
 * Имя подключённого телефона.
 *
 * Берём через рефлексию isConnected у BluetoothDevice: официального
 * способа узнать «подключено ли устройство прямо сейчас» без привязки
 * к профилю в Android нет, а привязка к A2DP асинхронная и на карточке
 * плеера успевала не всегда.
 *
 * Метод скрытый, но существует во всех версиях начиная с Android 4.4
 * и на китайских прошивках работает. Если вдруг пропадёт — вернём null
 * и карточка покажет просто «Bluetooth».
 */
object BtDevice {

    fun connectedName(context: Context): String? = runCatching {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
        if (adapter?.isEnabled != true) return null

        adapter.bondedDevices?.firstOrNull { dev ->
            runCatching {
                val m = dev.javaClass.getMethod("isConnected")
                m.invoke(dev) as? Boolean == true
            }.getOrDefault(false)
        }?.name
    }.getOrNull()
}
