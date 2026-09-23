package com.marketmaps.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap as AndroidBitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
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
 * خرائط جوجل + أيقونات مخصصة بحجم يتغير مع التكبير + أسماء اختيارية.
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
    showLabels: Boolean = true,
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

    var currentZoom by remember { mutableFloatStateOf(initialZoom) }

    LaunchedEffect(cameraPositionState.position.zoom) {
        currentZoom = cameraPositionState.position.zoom
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val pos = cameraPositionState.position
            currentZoom = pos.zoom
            onCameraIdle(pos.target.latitude, pos.target.longitude, pos.zoom)
        }
    }

    val latForScale = cameraPositionState.position.target.latitude
    val mode = MarkerIconHelper.displayModeForGoogleZoom(currentZoom, latForScale)

    val iconCache = remember(mode, showLabels) { mutableMapOf<String, BitmapDescriptor>() }

    fun storeIcon(store: Store): BitmapDescriptor? {
        if (mode == MarkerIconHelper.DisplayMode.HIDDEN) return null
        val cacheKey = "${store.id}|${mode.name}|$showLabels|${store.name}"
        iconCache[cacheKey]?.let { return it }
        val bmp: AndroidBitmap? = if (showLabels && mode != MarkerIconHelper.DisplayMode.CIRCLE) {
            MarkerIconHelper.getAndroidMarkerBitmapWithLabel(store.category, store.name, mode)
        } else {
            MarkerIconHelper.getAndroidMarkerBitmap(store.category, mode)
        } ?: return null
        val desc = BitmapDescriptorFactory.fromBitmap(bmp)
        iconCache[cacheKey] = desc
        return desc
    }

    val userIcon = remember(mode) {
        MarkerIconHelper.getAndroidUserLocationBitmap(mode)?.let {
            BitmapDescriptorFactory.fromBitmap(it)
        } ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
    }

    // نقطة الارتكاز: عند وجود نص أسفل الأيقونة نرفع الارتكاز قليلاً
    val markerAnchor = if (showLabels && mode != MarkerIconHelper.DisplayMode.CIRCLE) {
        Offset(0.5f, 0.62f)
    } else {
        Offset(0.5f, 1.0f)
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true,
            mapToolbarEnabled = false
        ),
        onMapLongClick = { latLng ->
            if (isAddMode) onLongPress(latLng.latitude, latLng.longitude)
        }
    ) {
        if (mode != MarkerIconHelper.DisplayMode.HIDDEN) {
            stores.forEach { store ->
                val icon = storeIcon(store) ?: return@forEach
                Marker(
                    state = MarkerState(position = LatLng(store.latitude, store.longitude)),
                    title = store.name,
                    snippet = store.category,
                    icon = icon,
                    anchor = markerAnchor,
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
                    icon = userIcon,
                    anchor = Offset(0.5f, 1.0f)
                )
            }
        }
    }
}
