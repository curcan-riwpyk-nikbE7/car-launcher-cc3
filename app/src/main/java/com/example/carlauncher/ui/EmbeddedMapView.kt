package com.example.carlauncher.ui

import android.preference.PreferenceManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.carlauncher.data.AppRepository
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Встроенная векторная GPS-карта местности внутри карточки авто.
 * Работает автономно через надежный автомобильный CDN CARTO Dark Matter / OSM,
 * передает уникальный User-Agent и не блокируется.
 */
@Composable
fun EmbeddedMapView(
    speedKmh: Int,
    onOpenFullNavi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val s = LocalThemeSpec.current

    val mapView = remember {
        Configuration.getInstance().load(
            context,
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        // Уникальный автомобильный User-Agent во избежание 403
        Configuration.getInstance().userAgentValue =
            "CarLauncherCC3/5.2 (Linux; Android Automotive; curcan-riwpyk-nikbE7)"

        // Очищаем кэш CartoDark (где был водяной знак API KEY) и старый Mapnik
        runCatching {
            val cacheDir = Configuration.getInstance().osmdroidTileCache
            val cartoDir = java.io.File(cacheDir, "CartoDark")
            if (cartoDir.exists()) cartoDir.deleteRecursively()
            val mapnikDir = java.io.File(cacheDir, "Mapnik")
            if (mapnikDir.exists()) mapnikDir.deleteRecursively()
        }

        MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)

            // Автомобильный темный фильтр без водяных знаков
            val darkMatrix = android.graphics.ColorMatrix(
                floatArrayOf(
                    -0.75f, 0f, 0f, 0f, 210f,
                    0f, -0.75f, 0f, 0f, 210f,
                    0f, 0f, -0.75f, 0f, 220f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            overlayManager.tilesOverlay.setColorFilter(android.graphics.ColorMatrixColorFilter(darkMatrix))

            // Дефолтная точка (центр), пока GPS не зафиксирует координаты
            controller.setCenter(GeoPoint(55.751244, 37.618423))

            // Отслеживание местоположения авто по GPS
            val locationProvider = GpsMyLocationProvider(context)
            val locationOverlay = MyLocationNewOverlay(locationProvider, this).apply {
                enableMyLocation()
                enableFollowLocation()
                setDrawAccuracyEnabled(false)
            }
            overlays.add(locationOverlay)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            runCatching {
                mapView.onPause()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(s.cardCorner))
            .background(s.carCardBg)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView }
        )

        // Плашка скорости в верхнем левом углу поверх карты
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Navigation,
                    contentDescription = null,
                    tint = s.accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$speedKmh",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = s.fontFamily
                )
                Text(
                    text = "км/ч",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = s.fontFamily
                )
            }
        }

        // Кнопка «Открыть в полный экран» в правом верхнем углу
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = {
                    if (onOpenFullNavi != {}) {
                        onOpenFullNavi()
                    } else {
                        AppRepository.launchFirstAvailable(context, AppRepository.NAVIGATION)
                    }
                }),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.OpenInFull,
                contentDescription = "Открыть навигатор",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
