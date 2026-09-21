package com.marketmaps.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.marketmaps.app.data.AppPreferences
import com.marketmaps.app.data.MapDownloader
import com.marketmaps.app.data.Store
import com.marketmaps.app.data.StoreRepository
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val storeRepository = remember { StoreRepository() }
    val appPreferences = remember { AppPreferences(context) }
    val savedLocation by appPreferences.lastLocation.collectAsState(initial = null)
    val offlineMode by appPreferences.offlineMode.collectAsState(initial = false)

    remember {
        AndroidGraphicFactory.createInstance(context.applicationContext)
    }

    var layerBundle by remember { mutableStateOf<MapLayerHelper.LayerBundle?>(null) }

    var isMenuExpanded by remember { mutableStateOf(false) }
    var isAddMode by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedLat by remember { mutableStateOf(0.0) }
    var selectedLon by remember { mutableStateOf(0.0) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var stores by remember { mutableStateOf<List<Store>>(emptyList()) }

    var searchQuery by remember { mutableStateOf("") }
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLon by remember { mutableStateOf<Double?>(null) }

    var selectedStore by remember { mutableStateOf<Store?>(null) }
    var storeToEdit by remember { mutableStateOf<Store?>(null) }

    var navResults by remember { mutableStateOf<List<StoreWithDistance>>(emptyList()) }
    var navIndex by remember { mutableStateOf(-1) }

    val searchResults = remember(searchQuery, stores, userLat, userLon) {
        filterAndSortStores(stores, searchQuery, userLat, userLon)
    }

    fun goToNavResult(index: Int) {
        if (index < 0 || index >= navResults.size) return
        navIndex = index
        val item = navResults[index]
        val store = item.store
        mapViewRef?.model?.mapViewPosition?.animateTo(LatLong(store.latitude, store.longitude))
        mapViewRef?.model?.mapViewPosition?.zoomLevel = 17.toByte()
        selectedStore = store
    }

    val isAddModeRef = remember { mutableStateOf(isAddMode) }
    isAddModeRef.value = isAddMode

    val storesRef = remember { mutableStateOf(stores) }
    storesRef.value = stores

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> layerBundle?.let { MapLayerHelper.resume(it) }
                Lifecycle.Event.ON_PAUSE -> layerBundle?.let { MapLayerHelper.pause(it) }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            layerBundle?.let { MapLayerHelper.pause(it) }
        }
    }

    LaunchedEffect(Unit) {
        val result = storeRepository.getAllStores()
        if (result.isSuccess) {
            stores = result.getOrDefault(emptyList())
        }
        tryGetLastLocation(context) { lat, lon ->
            userLat = lat
            userLon = lon
        }
    }

    LaunchedEffect(mapViewRef, savedLocation) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        val loc = savedLocation ?: return@LaunchedEffect
        mapView.model.mapViewPosition.setCenter(LatLong(loc.first, loc.second))
        mapView.model.mapViewPosition.zoomLevel = loc.third.toInt().toByte()
    }

    LaunchedEffect(offlineMode, mapViewRef) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        val bundle = layerBundle ?: return@LaunchedEffect
        if (offlineMode && MapDownloader.isEgyptMapDownloaded(context)) {
            MapLayerHelper.applyOffline(context, mapView, bundle)
        } else {
            MapLayerHelper.applyOnline(mapView, bundle)
        }
        addMarkersToMap(context, mapView, stores)
    }

    LaunchedEffect(mapViewRef, stores) {
        mapViewRef?.let { mapView ->
            addMarkersToMap(context, mapView, stores)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            moveToCurrentLocation(context, mapViewRef) { lat, lon ->
                userLat = lat
                userLon = lon
            }
        } else {
            Toast.makeText(context, "يجب السماح بالوصول إلى الموقع لاستخدام هذه الميزة", Toast.LENGTH_LONG).show()
        }
    }

    fun requestLocationAndMove() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            moveToCurrentLocation(context, mapViewRef) { lat, lon ->
                userLat = lat
                userLon = lon
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    fun refreshStores() {
        scope.launch {
            val refreshed = storeRepository.getAllStores()
            if (refreshed.isSuccess) {
                stores = refreshed.getOrDefault(emptyList())
            }
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AndroidView(
                factory = { ctx ->
                    val initialLat = savedLocation?.first ?: 30.0444
                    val initialLon = savedLocation?.second ?: 31.2357
                    val initialZoom = (savedLocation?.third ?: 14.0).toInt().toByte()
                    val mapView = MapView(ctx).apply {
                        isClickable = true
                        setBuiltInZoomControls(false)
                    }
                    val cache = MapLayerHelper.createTileCache(ctx, mapView)
                    val bundle = MapLayerHelper.LayerBundle(tileCache = cache)
                    layerBundle = bundle
                    MapLayerHelper.applyOnline(mapView, bundle)
                    mapView.model.mapViewPosition.setCenter(LatLong(initialLat, initialLon))
                    mapView.model.mapViewPosition.zoomLevel = initialZoom
                    mapViewRef = mapView

                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onLongPress(e: MotionEvent) {
                            if (isAddModeRef.value) {
                                val projection = mapView.mapViewProjection
                                val latLong = projection.fromPixels(e.x.toDouble(), e.y.toDouble())
                                selectedLat = latLong.latitude
                                selectedLon = latLong.longitude
                                showAddDialog = true
                            }
                        }

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            val projection = mapView.mapViewProjection
                            val tapped = projection.fromPixels(e.x.toDouble(), e.y.toDouble())
                            val nearest = findNearestStore(storesRef.value, tapped.latitude, tapped.longitude, 80.0)
                            if (nearest != null) {
                                selectedStore = nearest
                                return true
                            }
                            return false
                        }
                    })

                    mapView.setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
                        false
                    }
                    mapView
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView ->
                    mapViewRef = mapView
                    layerBundle?.let { MapLayerHelper.resume(it) }
                },
                onRelease = { mapView ->
                    layerBundle?.let { MapLayerHelper.destroy(it) }
                    mapView.destroy()
                    mapViewRef = null
                    layerBundle = null
                }
            )

            SearchBar(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    if (it.isBlank()) {
                        navResults = emptyList()
                        navIndex = -1
                    }
                },
                results = searchResults,
                onResultClick = { store ->
                    navResults = searchResults
                    navIndex = searchResults.indexOfFirst { it.store.id == store.id }.takeIf { it >= 0 } ?: 0
                    mapViewRef?.model?.mapViewPosition?.animateTo(LatLong(store.latitude, store.longitude))
                    mapViewRef?.model?.mapViewPosition?.zoomLevel = 17.toByte()
                    searchQuery = ""
                    selectedStore = store
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            FloatingActionButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp)
                    .size(48.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
            }

            if (navResults.isNotEmpty() && navIndex in navResults.indices) {
                val current = navResults[navIndex]
                val distanceText = if (current.distanceMeters >= 0) formatDistance(current.distanceMeters) else null
                SearchResultNav(
                    currentIndex = navIndex,
                    total = navResults.size,
                    storeName = current.store.name,
                    distanceText = distanceText,
                    onPrevious = { goToNavResult(navIndex - 1) },
                    onNext = { goToNavResult(navIndex + 1) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp)
                )
            }

            AnimatedVisibility(
                visible = isAddMode,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
            ) {
                Text(
                    text = "اضغط مطولاً على أي مكان في الخريطة لإضافة أو تعريف موقع جديد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                )
            }

            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = isMenuExpanded,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FloatingActionButton(
                            onClick = { requestLocationAndMove(); isMenuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = "موقعي الحالي")
                        }

                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    val wantOffline = !offlineMode
                                    if (wantOffline && !MapDownloader.isEgyptMapDownloaded(context)) {
                                        Toast.makeText(context, "حمّل خريطة مصر أولاً من الإعدادات", Toast.LENGTH_LONG).show()
                                        onOpenSettings()
                                    } else {
                                        appPreferences.setOfflineMode(wantOffline)
                                        Toast.makeText(
                                            context,
                                            if (wantOffline) "وضع بدون إنترنت" else "وضع الإنترنت",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    isMenuExpanded = false
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (offlineMode) Icons.Default.CloudOff else Icons.Default.Cloud,
                                contentDescription = if (offlineMode) "التبديل للإنترنت" else "التبديل للأوفلاين"
                            )
                        }

                        FloatingActionButton(
                            onClick = { isAddMode = !isAddMode; isMenuExpanded = false },
                            containerColor = if (isAddMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = if (isAddMode) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isAddMode) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = if (isAddMode) "إلغاء إضافة محل" else "إضافة محل"
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { isMenuExpanded = !isMenuExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (isMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (isMenuExpanded) "إغلاق القائمة" else "فتح القائمة"
                    )
                }
            }

            if (showAddDialog) {
                AddStoreDialog(
                    latitude = selectedLat,
                    longitude = selectedLon,
                    onDismiss = { showAddDialog = false },
                    onSave = { name, categoryPath, description ->
                        scope.launch {
                            val store = Store(
                                name = name,
                                category = categoryPath,
                                description = description,
                                latitude = selectedLat,
                                longitude = selectedLon
                            )
                            val result = storeRepository.addStore(store)
                            if (result.isSuccess) {
                                Toast.makeText(context, "تم حفظ المحل بنجاح", Toast.LENGTH_SHORT).show()
                                refreshStores()
                                showAddDialog = false
                                isAddMode = false
                            } else {
                                Toast.makeText(context, "فشل حفظ المحل: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            storeToEdit?.let { store ->
                AddStoreDialog(
                    latitude = store.latitude,
                    longitude = store.longitude,
                    initialStore = store,
                    onDismiss = { storeToEdit = null },
                    onSave = { name, categoryPath, description ->
                        scope.launch {
                            val updated = store.copy(name = name, category = categoryPath, description = description)
                            val result = storeRepository.updateStore(updated)
                            if (result.isSuccess) {
                                Toast.makeText(context, "تم تحديث المحل", Toast.LENGTH_SHORT).show()
                                refreshStores()
                                storeToEdit = null
                                selectedStore = null
                            } else {
                                Toast.makeText(context, "فشل التحديث: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            selectedStore?.let { store ->
                StoreDetailsDialog(
                    store = store,
                    onEdit = {
                        selectedStore = null
                        storeToEdit = it
                    },
                    onDelete = {
                        scope.launch {
                            val result = storeRepository.deleteStore(it.id)
                            if (result.isSuccess) {
                                Toast.makeText(context, "تم حذف المحل", Toast.LENGTH_SHORT).show()
                                val deletedId = it.id
                                refreshStores()
                                selectedStore = null
                                navResults = navResults.filter { item -> item.store.id != deletedId }
                                if (navResults.isEmpty()) navIndex = -1
                                else navIndex = navIndex.coerceIn(0, navResults.lastIndex)
                            } else {
                                Toast.makeText(context, "فشل الحذف: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onDismiss = { selectedStore = null }
                )
            }
        }
    }
}

private fun findNearestStore(stores: List<Store>, lat: Double, lon: Double, maxDistanceMeters: Double): Store? {
    var nearest: Store? = null
    var minDist = Double.MAX_VALUE
    stores.forEach { store ->
        val dist = haversineMeters(lat, lon, store.latitude, store.longitude)
        if (dist < minDist && dist <= maxDistanceMeters) {
            minDist = dist
            nearest = store
        }
    }
    return nearest
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun addMarkersToMap(context: Context, mapView: MapView, stores: List<Store>) {
    val layersToRemove = mapView.layerManager.layers.filterIsInstance<Marker>()
    layersToRemove.forEach { mapView.layerManager.layers.remove(it) }

    stores.forEach { store ->
        val bitmap = MarkerIconHelper.getMarkerBitmap(store.category)
        val marker = Marker(
            LatLong(store.latitude, store.longitude),
            bitmap,
            0,
            -bitmap.height / 2
        )
        mapView.layerManager.layers.add(marker)
    }
}

@SuppressLint("MissingPermission")
private fun moveToCurrentLocation(context: Context, mapView: MapView?, onLocation: ((Double, Double) -> Unit)? = null) {
    if (mapView == null) return
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
        .addOnSuccessListener { location ->
            if (location != null) {
                mapView.model.mapViewPosition.animateTo(LatLong(location.latitude, location.longitude))
                mapView.model.mapViewPosition.zoomLevel = 16.toByte()
                onLocation?.invoke(location.latitude, location.longitude)
                Toast.makeText(context, "تم تحديد موقعك الحالي", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "تعذر الحصول على الموقع، تأكد من تفعيل GPS", Toast.LENGTH_LONG).show()
            }
        }
        .addOnFailureListener {
            Toast.makeText(context, "حدث خطأ أثناء تحديد الموقع", Toast.LENGTH_LONG).show()
        }
}

@SuppressLint("MissingPermission")
private fun tryGetLastLocation(context: Context, onLocation: (Double, Double) -> Unit) {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return
    LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
        if (location != null) onLocation(location.latitude, location.longitude)
    }
}
