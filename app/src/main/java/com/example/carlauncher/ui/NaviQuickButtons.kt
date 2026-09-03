package com.example.carlauncher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carlauncher.data.AppRepository
import com.example.carlauncher.data.SettingsStore
import com.example.carlauncher.data.TripComputer

/**
 * Быстрые кнопки навигации, как на штатных лаунчерах ГУ (Reglink/CC3).
 *
 * Появляются ПОВЕРХ встроенной карты в карточке (см. CarCard), только
 * когда в карточку встроен навигатор.
 *
 * Поведение кнопок:
 *  - короткий тап — построить маршрут «домой»/«на работу» в навигаторе,
 *    который сейчас встроен в карточку;
 *  - долгий тап — запомнить ТЕКУЩЕЕ место (GPS) как «дом»/«работу».
 *    На кнопке видно, задана ли точка (точка-индикатор).
 *
 * Точки хранятся в SettingsStore, поэтому переживают перезапуск.
 * Если точка не задана, короткий тап просто открывает навигатор.
 */

private data class NavPoint(val lat: Double, val lon: Double)

private fun homePoint(): NavPoint? = SettingsStore.homeLat.value.takeIf { it != 0.0 }
    ?.let { NavPoint(it, SettingsStore.homeLon.value) }

private fun workPoint(): NavPoint? = SettingsStore.workLat.value.takeIf { it != 0.0 }
    ?.let { NavPoint(it, SettingsStore.workLon.value) }

private fun currentGps(): NavPoint? {
    val p = TripComputer.currentPosition() ?: return null
    return NavPoint(p.first, p.second)
}

/** Полупрозрачная панель быстрых кнопок поверх карты. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NaviQuickOverlay(
    modifier: Modifier = Modifier,
    navigatorPkg: String?
) {
    val context = LocalContext.current
    val isNavi = navigatorPkg != null && NaviRouter.looksLikeNavigator(navigatorPkg)
    if (!isNavi || navigatorPkg == null) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickNavButton(
            icon = Icons.Rounded.Home,
            label = "Домой",
            hasPoint = homePoint() != null,
            onClick = {
                val p = homePoint()
                // Не все навигаторы умеют строить маршрут по координатам
                // (у Navitel/Sygic нет такой схемы) — если не вышло,
                // просто открываем навигатор, чтобы не «молчать».
                if (p == null || !NaviRouter.routeTo(context, navigatorPkg, p.lat, p.lon)) {
                    AppRepository.launchPackage(context, navigatorPkg)
                }
            },
            onLongClick = {
                val p = currentGps()
                if (p != null) SettingsStore.setHome(p.lat, p.lon)
            }
        )
        QuickNavButton(
            icon = Icons.Rounded.Work,
            label = "Работа",
            hasPoint = workPoint() != null,
            onClick = {
                val p = workPoint()
                if (p == null || !NaviRouter.routeTo(context, navigatorPkg, p.lat, p.lon)) {
                    AppRepository.launchPackage(context, navigatorPkg)
                }
            },
            onLongClick = {
                val p = currentGps()
                if (p != null) SettingsStore.setWork(p.lat, p.lon)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickNavButton(
    icon: ImageVector,
    label: String,
    hasPoint: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bg = Color.Black.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.95f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        // Точка-индикатор: задано ли место. Пусто = «задать долгим тапом».
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (hasPoint) Color(0xFF4CD964)
                    else Color.White.copy(alpha = 0.25f)
                )
        )
    }
}

/**
 * Построение маршрута в нужном навигаторе.
 * Схемы под популярные навигаторы (точка lat/lon).
 */
object NaviRouter {

    /** Проверяет, выглядит ли приложение как навигатор (по списку). */
    fun looksLikeNavigator(packageName: String): Boolean =
        AppRepository.NAVIGATION.contains(packageName)

    /**
     * Строит маршрут в навигаторе [packageName] к точке (lat, lon).
     * @return true, если маршрут удалось передать навигатору
     */
    fun routeTo(context: Context, packageName: String, lat: Double, lon: Double): Boolean {
        val intent: Intent = when (packageName) {
            // Яндекс Навигатор: маршрут до координат
            "ru.yandex.yandexnavi" ->
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("yandexnavi://build_route_on_map?lat_to=$lat&lon_to=$lon")
                )

            // Яндекс Карты
            "ru.yandex.yandexmaps" ->
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~$lat,$lon&rtt=auto")
                )

            // Google Карты: навигация до точки
            "com.google.android.apps.maps" ->
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("google.navigation:?q=$lat,$lon")
                )

            // Waze
            "com.waze" ->
                Intent(Intent.ACTION_VIEW, Uri.parse("waze://?ll=$lat,$lon&navigate=yes"))

            // 2ГИС
            "ru.dublgis.dgismobile" ->
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("dgis://2gis.ru/route/from/to?to=$lat,$lon")
                )

            else -> return false
        }

        val resolved = runCatching {
            context.packageManager.resolveActivity(intent, 0) != null
        }.getOrDefault(false)
        if (!resolved) return false

        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
