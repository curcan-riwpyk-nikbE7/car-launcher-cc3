package com.example.carlauncher.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Запуск приложений сразу в нужном экране, а не на домашней странице.
 *
 * Обычный `getLaunchIntentForPackage` открывает стартовый экран приложения:
 * у Яндекс Карт это поиск «Чем заняться» с карточками рекомендаций,
 * у YouTube — лента с вкладками и нижним меню. В маленьком окне это
 * выглядит месивом.
 *
 * Здесь для каждого известного приложения подобран intent, который
 * открывает именно тот режим, который нужен в машине: карту с картой,
 * видео с видео.
 */
object AppIntents {

    /**
     * Возвращает intent «полезного» экрана приложения
     * или null, если специального нет и надо открывать как обычно.
     */
    fun preferredIntent(packageName: String): Intent? = when {

        // ── Навигация: сразу карта, без домашнего экрана ──

        packageName == "ru.yandex.yandexnavi" ->
            // Навигатор умеет открываться в режиме карты напрямую
            Intent(Intent.ACTION_VIEW, Uri.parse("yandexnavi://show_point_on_map"))
                .setPackage(packageName)

        packageName == "ru.yandex.yandexmaps" ->
            // geo: сразу показывает карту вместо стартовой страницы
            Intent(Intent.ACTION_VIEW, Uri.parse("yandexmaps://maps.yandex.ru/?l=map"))
                .setPackage(packageName)

        packageName == "com.google.android.apps.maps" ->
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?z=16"))
                .setPackage(packageName)

        packageName == "com.waze" ->
            Intent(Intent.ACTION_VIEW, Uri.parse("waze://?a=map"))
                .setPackage(packageName)

        packageName == "ru.dublgis.dgismobile" ->
            Intent(Intent.ACTION_VIEW, Uri.parse("dgis://2gis.ru/"))
                .setPackage(packageName)

        // ── Видео и музыка: свои экраны обычно и так уместны ──

        else -> null
    }

    /**
     * Универсальный запуск: сначала пробуем «полезный» экран,
     * при неудаче — обычный старт приложения.
     */
    fun bestIntent(context: Context, packageName: String): Intent? {
        val special = preferredIntent(packageName)
        if (special != null) {
            // Проверяем, что приложение действительно умеет это открыть,
            // иначе startActivity упадёт и пользователь ничего не увидит.
            val resolves = runCatching {
                context.packageManager.resolveActivity(special, 0) != null
            }.getOrDefault(false)
            if (resolves) return special
        }
        return context.packageManager.getLaunchIntentForPackage(packageName)
    }
}
