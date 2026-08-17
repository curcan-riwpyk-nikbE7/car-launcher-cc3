package com.example.carlauncher.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast

/**
 * Быстрые переключатели для шторки.
 *
 * Смысл файла в том, чтобы все ограничения Android были собраны в одном
 * месте, а не размазаны по UI. Каждая функция либо честно делает дело,
 * либо возвращает false — тогда шторка показывает системный экран,
 * а не притворяется, что сработала.
 */
object QuickControls {

    private const val TAG = "QuickControls"

    // ──────────────────────────── Bluetooth ────────────────────────────

    /**
     * Прямое включение и выключение BT.
     *
     * На Android 10 (наш случай) enable()/disable() ещё работают.
     * В Android 13 их закрыли окончательно, поэтому проверяем версию:
     * на новых прошивках просто открываем системные настройки, чтобы
     * кнопка не выглядела «нажалась, но ничего не произошло».
     */
    fun isBluetoothOn(context: Context): Boolean = runCatching {
        adapter(context)?.isEnabled == true
    }.getOrDefault(false)

    fun toggleBluetooth(context: Context): Boolean {
        val a = adapter(context) ?: return false

        // На Android 13 enable()/disable() объявлены недоступными для
        // обычных приложений. Но на этом ГУ прошивка собрана с test-keys
        // и раздаёт права свободнее стока, поэтому сначала пробуем —
        // и только если система откажет, уводим в настройки.
        //
        // Раньше здесь стоял безусловный отказ по версии, и кнопка
        // никогда не срабатывала, хотя железо позволяло.
        val direct = runCatching {
            @Suppress("DEPRECATION")
            if (a.isEnabled) a.disable() else a.enable()
        }.getOrDefault(false)
        if (direct) return true

        // Второй заход — через скрытый метод адаптера. Часть китайских
        // прошивок оставляет его открытым, когда публичный уже закрыт.
        return runCatching {
            val m = a.javaClass.getMethod(if (a.isEnabled) "disable" else "enable")
            m.invoke(a) as? Boolean == true
        }.getOrDefault(false)
    }

    private fun adapter(context: Context): BluetoothAdapter? = runCatching {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
    }.getOrNull()

    fun openBluetoothSettings(context: Context) {
        open(context, Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    // ───────────────────────────── Интернет ─────────────────────────────

    /**
     * Состояние Wi-Fi читать можно всегда, а вот включать с Android 10
     * нельзя: setWifiEnabled() возвращает false и ничего не делает.
     * Поэтому кнопка показывает состояние, а по нажатию открывает панель.
     */
    fun isWifiOn(context: Context): Boolean = runCatching {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    }.getOrDefault(false)

    /**
     * Системная панель интернета — выезжает поверх лаунчера, тумблер Wi-Fi
     * прямо в ней, лаунчер при этом остаётся на экране. Это официальная
     * замена запрещённому setWifiEnabled.
     *
     * Панель появилась в Android 10. Если её нет — открываем обычные
     * настройки Wi-Fi.
     */
    /**
     * Пробует включить или выключить Wi-Fi напрямую.
     *
     * С Android 10 setWifiEnabled закрыт для обычных приложений —
     * возвращает false и ничего не делает. Но сборке, подписанной
     * ключом платформы, система его оставляет: она считает такое
     * приложение своим.
     *
     * @return false, если прямой путь недоступен — тогда остаётся
     *   открыть системную панель
     */
    fun toggleWifi(context: Context): Boolean = runCatching {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val target = !wm.isWifiEnabled
        @Suppress("DEPRECATION")
        val ok = wm.setWifiEnabled(target)
        // На Android 10+ метод молча возвращает false — проверяем
        // по факту, а не по возвращённому значению.
        ok && wm.isWifiEnabled == target
    }.getOrDefault(false)

    /**
     * Мобильный интернет.
     *
     * Публичного способа переключить его нет ни в одной версии Android:
     * setMobileDataEnabled удалён, а замена скрыта. Работает только
     * через рефлексию и только с системными правами.
     */
    fun isMobileDataOn(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, "mobile_data") == 1
    }.getOrDefault(false)

    fun toggleMobileData(context: Context): Boolean = runCatching {
        val tm = context.applicationContext
            .getSystemService(Context.TELEPHONY_SERVICE)
        val target = !isMobileDataOn(context)
        val m = tm.javaClass.getDeclaredMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
        m.isAccessible = true
        m.invoke(tm, target)
        isMobileDataOn(context) == target
    }.getOrElse {
        Log.w(TAG, "Мобильные данные переключить не удалось", it)
        false
    }

    fun openMobileDataSettings(context: Context) {
        if (!open(context, "android.settings.panel.action.MOBILE_DATA")) {
            open(context, Settings.ACTION_DATA_ROAMING_SETTINGS)
        }
    }

    fun openInternetPanel(context: Context) {
        val opened = Build.VERSION.SDK_INT >= 29 &&
            open(context, "android.settings.panel.action.INTERNET_CONNECTIVITY")
        if (!opened) open(context, Settings.ACTION_WIFI_SETTINGS)
    }

    // ───────────────────────────── Громкость ─────────────────────────────

    fun isMuted(context: Context): Boolean = runCatching {
        am(context).getStreamVolume(AudioManager.STREAM_MUSIC) == 0
    }.getOrDefault(false)

    /**
     * Тишина с запоминанием прежнего уровня: повторное нажатие возвращает
     * ровно ту громкость, что была, а не какую-то среднюю.
     */
    private var volumeBeforeMute = -1

    fun toggleMute(context: Context) {
        runCatching {
            val a = am(context)
            val cur = a.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (cur > 0) {
                volumeBeforeMute = cur
                a.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            } else {
                val back = if (volumeBeforeMute > 0) volumeBeforeMute
                else a.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 3
                a.setStreamVolume(AudioManager.STREAM_MUSIC, back, 0)
            }
        }
    }

    private fun am(context: Context) =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ────────────────────────────── Яркость ──────────────────────────────

    /**
     * Яркость всей системы требует WRITE_SETTINGS. Разрешение особое:
     * его не спрашивают диалогом, пользователя надо отправить на
     * системный экран с тумблером. Спрашиваем один раз — при первом
     * движении ползунка.
     */
    fun canWriteSettings(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(context)

    fun requestWriteSettings(context: Context) {
        runCatching {
            val i = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            Toast.makeText(context, "Разрешите изменение настроек — это нужно для яркости", Toast.LENGTH_LONG).show()
        }
    }

    /** Текущая яркость 0..1. */
    fun brightness(context: Context): Float = runCatching {
        val v = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        (v / 255f).coerceIn(0f, 1f)
    }.getOrDefault(0.5f)

    /**
     * Ставит яркость. Заодно снимает автояркость: иначе датчик через
     * секунду перебьёт выставленное значение и ползунок «отпрыгнет».
     */
    fun setBrightness(context: Context, value: Float): Boolean {
        if (!canWriteSettings(context)) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            // Ниже 10 из 255 экран на многих ГУ гаснет полностью —
            // вернуть его потом можно только вслепую.
            val v = (value.coerceIn(0f, 1f) * 255).toInt().coerceIn(10, 255)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v)
            true
        }.getOrElse {
            Log.w(TAG, "Не удалось выставить яркость", it)
            false
        }
    }

    // ────────────────────────────── Прочее ──────────────────────────────

    private fun open(context: Context, action: String): Boolean = runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
