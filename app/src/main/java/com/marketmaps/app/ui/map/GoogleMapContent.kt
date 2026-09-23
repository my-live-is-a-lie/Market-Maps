package com.marketmaps.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.marketmaps.app.data.Store

/**
 * طبقة خرائط جوجل — تُعرض عند اختيار مزوّد GOOGLE.
 */
@Composable
fun GoogleMapContent(
    initialLat: Double,
    initialLon: Double,
    initialZoom: Float,
    stores: List<Store>,
    userLat: Double?,
    userLon: Double?,
    cameraTarget: Triple<Double, Double, Float>?,
    isAddMode: Boolean,
    onLongPress: (Double, Double) -> Unit,
    onMarkerClick: (Store) -> Unit,
    onCameraIdle: (Double, Double, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(initialLat, initialLon), initialZoom)
    }

    LaunchedEffect(cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(target.first, target.second), target.third)
        )
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = hasLocationPermission
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false
        ),
        onMapLongClick = { latLng ->
            if (isAddMode) onLongPress(latLng.latitude, latLng.longitude)
        },
        onMapClick = { /* تجاهل */ }
    ) {
        stores.forEach { store ->
            val hue = hueForCategory(store.category)
            Marker(
                state = MarkerState(position = LatLng(store.latitude, store.longitude)),
                title = store.name,
                snippet = store.category,
                icon = BitmapDescriptorFactory.defaultMarker(hue),
                onClick = {
                    onMarkerClick(store)
                    true
                }
            )
        }
        if (userLat != null && userLon != null) {
            Marker(
                state = MarkerState(position = LatLng(userLat, userLon)),
                title = "موقعي",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
        }
    }

    // حفظ موضع الكاميرا عند التوقف عن التحريك
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val pos = cameraPositionState.position
            onCameraIdle(pos.target.latitude, pos.target.longitude, pos.zoom)
        }
    }
}

private fun hueForCategory(category: String): Float {
    val c = category.lowercase()
    return when {
        listOf("صيدلية", "مستشفى", "عيادة", "مختبر", "أسنان").any { it in c } ->
            BitmapDescriptorFactory.HUE_RED
        listOf("مطعم", "مقهى", "كافي", "وجبات").any { it in c } ->
            BitmapDescriptorFactory.HUE_ORANGE
        listOf("بقالة", "عطارة", "سوبر").any { it in c } ->
            BitmapDescriptorFactory.HUE_GREEN
        listOf("ورشة", "سمكرة", "نجارة", "حدادة", "ميكانيكا").any { it in c } ->
            BitmapDescriptorFactory.HUE_VIOLET
        listOf("مدرسة", "ابتدائية", "إعدادية", "ثانوية").any { it in c } ->
            BitmapDescriptorFactory.HUE_AZURE
        else -> BitmapDescriptorFactory.HUE_BLUE
    }
}
