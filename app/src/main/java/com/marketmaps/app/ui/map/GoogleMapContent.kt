package com.marketmaps.app.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
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

@Composable
fun GoogleMapContent(
    stores: List<Store>,
    initialLat: Double,
    initialLon: Double,
    initialZoom: Float,
    userLat: Double?,
    userLon: Double?,
    targetLat: Double?,
    targetLon: Double?,
    targetZoom: Float?,
    onMapLongClick: (Double, Double) -> Unit,
    onStoreClick: (Store) -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(initialLat, initialLon), initialZoom)
    }

    LaunchedEffect(targetLat, targetLon, targetZoom) {
        if (targetLat != null && targetLon != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(targetLat, targetLon),
                    targetZoom ?: 17f
                )
            )
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = false // نرسم علامة مخصصة
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false
        ),
        onMapLongClick = { latLng ->
            onMapLongClick(latLng.latitude, latLng.longitude)
        }
    ) {
        stores.forEach { store ->
            val hue = categoryToHue(store.category)
            Marker(
                state = MarkerState(position = LatLng(store.latitude, store.longitude)),
                title = store.name,
                snippet = store.category,
                icon = BitmapDescriptorFactory.defaultMarker(hue),
                onClick = {
                    onStoreClick(store)
                    true
                }
            )
        }

        if (userLat != null && userLon != null) {
            Marker(
                state = MarkerState(position = LatLng(userLat, userLon)),
                title = "موقعي",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )
        }
    }
}

private fun categoryToHue(category: String): Float {
    val c = category.lowercase()
    return when {
        listOf("صيدلية", "مستشفى", "عيادة").any { it in c } -> BitmapDescriptorFactory.HUE_RED
        listOf("مطعم", "مقهى", "وجبات").any { it in c } -> BitmapDescriptorFactory.HUE_ORANGE
        listOf("بقالة", "عطارة").any { it in c } -> BitmapDescriptorFactory.HUE_GREEN
        listOf("ورشة", "سمكرة", "ميكانيكا").any { it in c } -> BitmapDescriptorFactory.HUE_VIOLET
        listOf("مدرسة", "مكتبة").any { it in c } -> BitmapDescriptorFactory.HUE_BLUE
        listOf("ملابس", "أحذية").any { it in c } -> BitmapDescriptorFactory.HUE_ROSE
        else -> BitmapDescriptorFactory.HUE_AZURE
    }
}
