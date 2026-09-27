package com.marketmaps.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap as AndroidBitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.marketmaps.app.util.logged
import androidx.compose.runtime.key
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.marketmaps.app.data.Store
import kotlin.math.roundToInt

/**
 * خرائط جوجل + أيقونات مخصصة بحجم يتغير مع التكبير + أسماء اختيارية.
 * BitmapDescriptorFactory يُستدعى فقط بعد تهيئة الخريطة (onMapLoaded).
 */
/** طلب تحريك الكاميرا — id فريد يعيد التشغيل حتى لنفس الإحداثيات */
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
    highlightedStoreId: String? = null,
    highlightScale: Float = 1f,
    onLongPress: (Double, Double) -> Unit,
    onMarkerClick: (Store) -> Unit,
    onCameraIdle: (Double, Double, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // تهيئة مصنع الأيقونات مبكراً لتفادي: IBitmapDescriptorFactory is not initialized
    LaunchedEffect(Unit) {
        logged(TAG, "MapsInitializer.initialize") { MapsInitializer.initialize(context) }
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

    var mapReady by remember { mutableStateOf(false) }

    // لا نحفظ الموقع قبل اكتمال تحميل الخريطة، ولا قبل أول حركة حقيقية للكاميرا:
    // الحالة الأولى (isMoving = false قبل أي حركة) ليست اختيار المستخدم.
    var cameraHasMoved by remember { mutableStateOf(false) }
    LaunchedEffect(cameraPositionState.isMoving, mapReady) {
        if (!mapReady) return@LaunchedEffect
        if (cameraPositionState.isMoving) {
            cameraHasMoved = true
        } else if (cameraHasMoved) {
            val pos = cameraPositionState.position
            onCameraIdle(pos.target.latitude, pos.target.longitude, pos.zoom)
        }
    }

    // سابقاً: قراءة cameraPositionState.position داخل التركيب (LaunchedEffect(position.zoom)
    // و latForScale) كانت تعيد تركيب الدالة كلها وحلقة العلامات مع كل إطار تحريك.
    // derivedStateOf يقرأ الموضع داخلياً ولا يُبلغ إلا عند تغيّر وضع العرض فعلاً
    // (عند عبور التكبير 12 / 14 / 16).
    val mode by remember {
        derivedStateOf {
            MarkerIconHelper.displayModeForGoogleZoom(cameraPositionState.position.zoom)
        }
    }

    val iconCache = remember(mode, showLabels, mapReady) { HashMap<String, BitmapDescriptor>() }

    fun storeIcon(store: Store, scale: Float): BitmapDescriptor? {
        if (!mapReady || mode == MarkerIconHelper.DisplayMode.HIDDEN) return null
        // تقريب المقياس لتقليل إدخالات الكاش أثناء أنيميشن التمييز
        val scaleKey = (scale * 25f).toInt()
        val withLabel = showLabels && mode != MarkerIconHelper.DisplayMode.CIRCLE
        // بدون أسماء: الأيقونة تعتمد على التصنيف فقط، فتتشارك كل محلات التصنيف نفس
        // الـ BitmapDescriptor بدل نسخة لكل محل.
        val cacheKey = if (withLabel) "L|${store.id}|${store.name}|${store.category}|$scaleKey"
        else "I|${store.category}|$scaleKey"
        iconCache[cacheKey]?.let { return it }
        // الصورة تُرسم بحجمها النهائي (مع مقياس التمييز) بدل تكبيرها بعد الرسم
        val bmp: AndroidBitmap = if (withLabel) {
            MarkerIconHelper.getAndroidMarkerBitmapWithLabel(store.category, store.name, mode, scale)
        } else {
            MarkerIconHelper.getAndroidMarkerBitmap(
                store.category, mode,
                (MarkerIconHelper.markerSizePxForMode(mode) * scale).roundToInt()
            )
        } ?: return null
        return logged(TAG, "BitmapDescriptor لـ ${store.category}") {
            BitmapDescriptorFactory.fromBitmap(bmp).also { iconCache[cacheKey] = it }
        }
    }

    // سابقاً كانت تُرسم صورة دبوس جديدة وتُنشأ BitmapDescriptor جديد مع كل إعادة تركيب
    val userIcon: BitmapDescriptor? = remember(mode, mapReady) {
        if (!mapReady || mode == MarkerIconHelper.DisplayMode.HIDDEN) {
            null
        } else {
            MarkerIconHelper.getAndroidUserLocationBitmap(mode)?.let { bmp ->
                logged(TAG, "BitmapDescriptor لموقع المستخدم") { BitmapDescriptorFactory.fromBitmap(bmp) }
            }
        }
    }

    val markerAnchor = if (showLabels && mode != MarkerIconHelper.DisplayMode.CIRCLE) {
        Offset(0.5f, 0.62f)
    } else {
        Offset(0.5f, 1.0f)
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = false,
            mapType = MapType.NORMAL,
            isBuildingEnabled = false
        ),
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
        if (mapReady && mode != MarkerIconHelper.DisplayMode.HIDDEN) {
            stores.forEach { store ->
                // key: كل علامة تحتفظ بحالتها بين إعادات التركيب؛ إضافة/حذف محل لا يعيد
                // إنشاء العلامات الأخرى، و MarkerState لا يُنشأ من جديد لكل علامة في كل مرة.
                key(store.id) {
                    val highlighted = highlightedStoreId != null && store.id == highlightedStoreId
                    val scale = if (highlighted) highlightScale else 1f
                    val icon = storeIcon(store, scale)
                    if (icon != null) {
                        val position = LatLng(store.latitude, store.longitude)
                        val markerState = rememberMarkerState(position = position)
                        Marker(
                            state = markerState,
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
                }
            }

            if (userLat != null && userLon != null && userIcon != null) {
                val userPosition = LatLng(userLat, userLon)
                val userState = rememberMarkerState(position = userPosition)
                Marker(
                    state = userState,
                    title = "موقعي",
                    icon = userIcon,
                    anchor = Offset(0.5f, 1.0f)
                )
            }
        }
    }
}

private const val TAG = "GoogleMap"
