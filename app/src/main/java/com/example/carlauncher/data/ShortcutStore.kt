package com.example.carlauncher.data

import android.content.Context

/**
 * Хранит, какое приложение пользователь назначил на плитку.
 * Долгое нажатие на плитку -> выбор приложения -> сохраняется сюда.
 */
class ShortcutStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("car_launcher_shortcuts", Context.MODE_PRIVATE)

    fun get(slot: String): String? = prefs.getString(slot, null)

    fun set(slot: String, value: String) {
        prefs.edit().putString(slot, value).apply()
    }

    fun clear(slot: String) {
        prefs.edit().remove(slot).apply()
    }

    /**
     * Скрытые из меню приложения.
     *
     * У штатного лаунчера такого нет — там весь системный мусор
     * висит в общем списке. На реальном ГУ это полсотни иконок,
     * половина из которых служебные: три «Камеры», два «Aux»,
     * тесты GPS. Прячем их из меню, с устройства ничего не удаляем.
     */
    fun hiddenApps(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()

    fun hideApp(pkg: String) {
        prefs.edit().putStringSet(KEY_HIDDEN, hiddenApps() + pkg).apply()
    }

    fun unhideApp(pkg: String) {
        prefs.edit().putStringSet(KEY_HIDDEN, hiddenApps() - pkg).apply()
    }

    fun unhideAll() {
        prefs.edit().remove(KEY_HIDDEN).apply()
    }

    /**
     * Первичное заполнение ярлыков.
     *
     * Пять пустых кружков «Add» на свежей установке выглядят так, будто
     * лаунчер сломан. У штатного CC3 ярлыки заполнены с завода, поэтому
     * при первом запуске подставляем то, что реально стоит на устройстве:
     * навигацию, видео, браузер, музыку, телефон.
     *
     * Делается один раз — флаг в тех же настройках. Если пользователь
     * очистил слот намеренно, повторно он не заполнится.
     */
    fun seedDefaults(context: Context, installed: List<String>) {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()

        val wanted = listOf(
            SLOT_1 to listOf("ru.yandex.yandexnavi", "ru.yandex.yandexmaps",
                "com.google.android.apps.maps"),
            SLOT_2 to listOf("com.google.android.youtube",
                "app.revanced.android.youtube"),
            SLOT_3 to listOf("com.android.chrome", "com.google.android.chrome"),
            SLOT_4 to listOf("ru.yandex.music", "com.spotify.music",
                "com.google.android.apps.youtube.music"),
            SLOT_5 to listOf("com.google.android.dialer", "com.android.dialer",
                "com.hzbhd.bt", "com.syu.bt")
        )

        val e = prefs.edit()
        for ((slot, candidates) in wanted) {
            if (prefs.contains(slot)) continue
            candidates.firstOrNull { it in installed }?.let { e.putString(slot, it) }
        }
        e.apply()
    }


    /**
     * Пакет приложения, которое держит Bluetooth-аудиоканал.
     * Список известных имён покрывает не все прошивки, поэтому даём
     * выбрать вручную — это надёжнее любых догадок.
     */
    fun setBtMusicApp(pkg: String?) {
        if (pkg == null) prefs.edit().remove(BtMusicStarter.KEY_BT_APP).apply()
        else prefs.edit().putString(BtMusicStarter.KEY_BT_APP, pkg).apply()
    }

    fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)

    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    companion object {
        const val KEY_SEEDED = "shortcuts_seeded"
        const val KEY_HIDDEN = "hidden_apps"
        const val SLOT_RADIO = "slot_radio"
        const val SLOT_RADIO_NAME = "slot_radio_name"
        const val SLOT_MUSIC = "slot_music"
        const val SLOT_NAV = "slot_nav"
        const val SLOT_1 = "slot_fav_1"
        const val SLOT_2 = "slot_fav_2"
        const val SLOT_3 = "slot_fav_3"
        const val SLOT_4 = "slot_fav_4"
        const val SLOT_5 = "slot_fav_5"
        const val SLOT_6 = "slot_fav_6"
        const val SLOT_7 = "slot_fav_7"
        const val SLOT_8 = "slot_fav_8"
        const val SLOT_9 = "slot_fav_9"
        const val SLOT_10 = "slot_fav_10"

        /** Приложение, запускаемое с виджета спидометра. */
        const val SLOT_SPEED = "slot_speed"

        /** id системного виджета, показываемого вместо спидометра. */
        const val KEY_SPEED_WIDGET = "key_speed_widget"

        /** Выбранная тема оформления. */
        const val KEY_THEME = "key_theme"
    }
}
