package com.marketmaps.app.ui.map

import android.graphics.Bitmap as AndroidBitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.key
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.marketmaps.app.data.Store

/**
 * خرائط جوجل — منطق العرض كما النسخة المستقرة السابقة.
 */
data class CameraTarget(
    val lat: Double,
    val lon: Double,
    val zoom: Float,
    val id: Long = System.nanoTime()
)

@Composable
fun GoogleMapContent(
    initialLat: Double,
    initialLon: Double,
    initialZoom: Float,
    stores: List<Store>,
    userLat: Double?,
    userLon: Double?,
    cameraTarget: CameraTarget?,
    isAddMode: Boolean,
    showLabels: Boolean = true,
    /** وضع ليلي: يُطبَّق نمط خريطة جوجل الليلي (يُمرَّر true فقط في الوضع الغامق/المظلم) */
    nightMode: Boolean = false,
    highlightedStoreId: String? = null,
    highlightScale: Float = 1f,
    routePoints: List<RoutePoint> = emptyList(),
    onLongPress: (Double, Double) -> Unit,
    onMarkerClick: (Store) -> Unit,
    onCameraIdle: (Double, Double, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    /**
     * الوضع الليلي من جوجل نفسه: **MapColorScheme.DARK** — نفس تلوين تطبيق خرائط جوجل
     * الرسمي (الأنهار والخضرة والطرق وأيقونات الأماكن والتسميات بألوان جوجل الجديدة).
     *
     * سبب إزالة النمط المخصص (JSON): وثائق جوجل تنص أن النمط المخصص **يُلغي الوضع الداكن**،
     * وهو أيضاً ما كان يُخفي أيقونات الأماكن والتسميات (النمط المخصص يستبدل تلوين جوجل).
     *
     * يُمرَّر المخطط عند إنشاء الخريطة، وتُعاد إنشاؤها عند تبديل الوضع فقط ([key])،
     * وموضع الكاميرا محفوظ في cameraPositionState المُعرَّفة خارج key.
     */
    val mapProperties = remember { MapProperties(isMyLocationEnabled = false) }

    LaunchedEffect(Unit) {
        try {
            MapsInitializer.initialize(context)
        } catch (_: Exception) {
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(initialLat, initialLon), initialZoom)
    }

    LaunchedEffect(cameraTarget?.id) {
        val target = cameraTarget ?: return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lon), target.zoom)
        )
    }

    var currentZoom by remember { mutableFloatStateOf(initialZoom) }
    var mapReady by remember { mutableStateOf(false) }
    var cameraHasMoved by remember { mutableStateOf(false) }

    // عند تبديل الوضع الليلي تُنشأ الخريطة من جديد بمخطط ألوان جوجل الجديد
    LaunchedEffect(nightMode) { mapReady = false }

    LaunchedEffect(cameraPositionState.position.zoom) {
        currentZoom = cameraPositionState.position.zoom
    }

    LaunchedEffect(routePoints, mapReady) {
        if (routePoints.size >= 2 && mapReady) {
            val bounds = LatLngBounds.builder()
            routePoints.forEach { point -> bounds.include(LatLng(point.latitude, point.longitude)) }
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(bounds.build(), 96)
            )
        }
    }

    // لا نحفظ الموقع قبل اكتمال التحميل وقبل أول حركة حقيقية
    LaunchedEffect(cameraPositionState.isMoving, mapReady) {
        if (!mapReady) return@LaunchedEffect
        if (cameraPositionState.isMoving) {
            cameraHasMoved = true
        } else if (cameraHasMoved) {
            val pos = cameraPositionState.position
            currentZoom = pos.zoom
            onCameraIdle(pos.target.latitude, pos.target.longitude, pos.zoom)
        }
    }

    val latForScale = cameraPositionState.position.target.latitude
    val mode = MarkerIconHelper.displayModeForGoogleZoom(currentZoom, latForScale)

    val iconCache = remember(mode, showLabels, mapReady, nightMode) { mutableMapOf<String, BitmapDescriptor>() }

    fun storeIcon(store: Store, scale: Float): BitmapDescriptor? {
        if (!mapReady) return null
        if (mode == MarkerIconHelper.DisplayMode.HIDDEN) return null
        val scaleKey = (scale * 20f).toInt()
        val cacheKey = "${store.id}|${mode.name}|$showLabels|$nightMode|${store.name}|s=$scaleKey"
        iconCache[cacheKey]?.let { return it }
        val sizePx = (MarkerIconHelper.markerSizePxForMode(mode) * scale).toInt().coerceAtLeast(1)
        val rawBmp: AndroidBitmap? = if (showLabels && mode != MarkerIconHelper.DisplayMode.CIRCLE) {
            MarkerIconHelper.getAndroidMarkerBitmapWithLabel(
                store.category, store.name, mode, scale, night = nightMode
            )
        } else {
            MarkerIconHelper.getAndroidMarkerBitmap(store.category, mode, sizePx)
        }
        val bmp = rawBmp ?: return null
        return try {
            val desc = BitmapDescriptorFactory.fromBitmap(bmp)
            iconCache[cacheKey] = desc
            desc
        } catch (_: Exception) {
            null
        }
    }

    fun userIcon(): BitmapDescriptor? {
        if (!mapReady) return null
        if (mode == MarkerIconHelper.DisplayMode.HIDDEN) return null
        val bmp = MarkerIconHelper.getAndroidUserLocationBitmap(mode) ?: return null
        return try {
            BitmapDescriptorFactory.fromBitmap(bmp)
        } catch (_: Exception) {
            null
        }
    }

    val markerAnchor = if (showLabels && mode != MarkerIconHelper.DisplayMode.CIRCLE) {
        Offset(0.5f, 0.62f)
    } else {
        Offset(0.5f, 1.0f)
    }

    key(nightMode) {
        GoogleMap(
            modifier = modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            googleMapOptionsFactory = {
                GoogleMapOptions().mapColorScheme(
                    if (nightMode) MapColorScheme.DARK else MapColorScheme.LIGHT
                )
            },
            properties = mapProperties,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = true,
                mapToolbarEnabled = false
            ),
            onMapLoaded = { mapReady = true },
            onMapLongClick = { latLng ->
                if (isAddMode) onLongPress(latLng.latitude, latLng.longitude)
            }
        ) {
            if (mapReady && routePoints.size >= 2) {
                Polyline(
                    points = routePoints.map { LatLng(it.latitude, it.longitude) },
                    color = Color(0xFF1769E0),
                    width = 7f,
                    zIndex = 20f
                )
            }

            if (mapReady && mode != MarkerIconHelper.DisplayMode.HIDDEN) {
                stores.forEach { store ->
                    val highlighted = highlightedStoreId != null && store.id == highlightedStoreId
                    val scale = if (highlighted) highlightScale else 1f
                    val icon = storeIcon(store, scale) ?: return@forEach
                    Marker(
                        state = MarkerState(position = LatLng(store.latitude, store.longitude)),
                        title = store.name,
                        snippet = store.category,
                        icon = icon,
                        anchor = markerAnchor,
                        zIndex = if (highlighted) 5f else 0f,
                        onClick = {
                            onMarkerClick(store)
                            true
                        }
                    )
                }

                if (userLat != null && userLon != null) {
                    val uIcon = userIcon()
                    if (uIcon != null) {
                        Marker(
                            state = MarkerState(position = LatLng(userLat, userLon)),
                            title = "موقعي",
                            icon = uIcon,
                            anchor = Offset(0.5f, 1.0f)
                        )
                    }
                }
            }
        }
    }
}
