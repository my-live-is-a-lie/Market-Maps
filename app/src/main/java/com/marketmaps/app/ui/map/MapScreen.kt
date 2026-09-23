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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.marketmaps.app.data.AppPreferences
import com.marketmaps.app.data.MapDownloader
import com.marketmaps.app.data.MapProvider
import com.marketmaps.app.data.Store
import com.marketmaps.app.data.StoreRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalFoundationApi::class)
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
    val recentSearches by appPreferences.recentSearches.collectAsState(initial = emptyList())
    val recentSearchLimit by appPreferences.recentSearchLimit.collectAsState(initial = 5)
    val mapProvider by appPreferences.mapProvider.collectAsState(initial = MapProvider.MAPSFORGE)

    remember {
        AndroidGraphicFactory.createInstance(context.applicationContext)
        MarkerIconHelper.init(context)
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
    var searchExpanded by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf("الكل") }
    var filterSub by remember { mutableStateOf("الكل") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLon by remember { mutableStateOf<Double?>(null) }
    var selectedStore by remember { mutableStateOf<Store?>(null) }
    var storeToEdit by remember { mutableStateOf<Store?>(null) }
    var navResults by remember { mutableStateOf<List<StoreWithDistance>>(emptyList()) }
    var navIndex by remember { mutableStateOf(-1) }
    var cameraTarget by remember { mutableStateOf<Triple<Double, Double, Float>?>(null) }

    val searchResults = remember(searchQuery, stores, userLat, userLon, filterType, filterSub) {
        filterAndSortStores(stores, searchQuery, userLat, userLon, filterType, filterSub)
    }

    fun moveCamera(lat: Double, lon: Double, zoom: Float = 17f) {
        cameraTarget = Triple(lat, lon, zoom)
        mapViewRef?.model?.mapViewPosition?.animateTo(LatLong(lat, lon))
        mapViewRef?.model?.mapViewPosition?.zoomLevel = zoom.toInt().toByte()
    }

    fun goToNavResult(index: Int) {
        if (index < 0 || index >= navResults.size) return
        navIndex = index
        val store = navResults[index].store
        moveCamera(store.latitude, store.longitude, 17f)
    }

    val isAddModeRef = remember { mutableStateOf(isAddMode) }
    isAddModeRef.value = isAddMode
    val storesRef = remember { mutableStateOf(stores) }
    storesRef.value = stores
    val userLatRef = remember { mutableStateOf(userLat) }
    userLatRef.value = userLat
    val userLonRef = remember { mutableStateOf(userLon) }
    userLonRef.value = userLon

    fun saveCameraPosition() {
        val mv = mapViewRef ?: return
        try {
            val center = mv.model.mapViewPosition.center
            val zoom = mv.model.mapViewPosition.zoomLevel.toDouble()
            scope.launch {
                appPreferences.saveLastLocation(center.latitude, center.longitude, zoom)
            }
        } catch (_: Exception) {
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> layerBundle?.let { MapLayerHelper.resume(it) }
                Lifecycle.Event.ON_PAUSE -> {
                    saveCameraPosition()
                    layerBundle?.let { MapLayerHelper.pause(it) }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            saveCameraPosition()
            layerBundle?.let { MapLayerHelper.pause(it) }
        }
    }

    LaunchedEffect(Unit) {
        val result = storeRepository.getAllStores()
        if (result.isSuccess) stores = result.getOrDefault(emptyList())
        tryGetLastLocation(context) { lat, lon -> userLat = lat; userLon = lon }
        if (appPreferences.rememberFilter.first()) {
            filterType = appPreferences.savedFilterType.first()
            filterSub = appPreferences.savedFilterSub.first()
        } else {
            filterType = "الكل"; filterSub = "الكل"
        }
    }

    var appliedSavedCamera by remember { mutableStateOf(false) }
    LaunchedEffect(mapViewRef) {
        val mapView = mapViewRef ?: return@LaunchedEffect
        if (appliedSavedCamera) return@LaunchedEffect
        val loc = appPreferences.lastLocation.first()
        if (loc != null) {
            mapView.model.mapViewPosition.setCenter(LatLong(loc.first, loc.second))
            mapView.model.mapViewPosition.zoomLevel = loc.third.toInt().toByte()
        }
        appliedSavedCamera = true
    }

    LaunchedEffect(offlineMode, mapViewRef, mapProvider) {
        if (mapProvider != MapProvider.MAPSFORGE) return@LaunchedEffect
        val mapView = mapViewRef ?: return@LaunchedEffect
        val bundle = layerBundle ?: return@LaunchedEffect
        if (offlineMode && MapDownloader.isEgyptMapDownloaded(context)) MapLayerHelper.applyOffline(context, mapView, bundle)
        else MapLayerHelper.applyOnline(mapView, bundle)
        addMarkersToMap(context, mapView, stores, userLat, userLon)
    }

    LaunchedEffect(mapViewRef, stores, userLat, userLon, mapProvider) {
        if (mapProvider != MapProvider.MAPSFORGE) return@LaunchedEffect
        mapViewRef?.let { addMarkersToMap(context, it, stores, userLat, userLon) }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            moveToCurrentLocation(context, mapViewRef) { lat, lon -> userLat = lat; userLon = lon; moveCamera(lat, lon, 18f) }
        } else Toast.makeText(context, "يجب السماح بالوصول إلى الموقع", Toast.LENGTH_LONG).show()
    }

    fun requestLocationAndMove() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            moveToCurrentLocation(context, mapViewRef) { lat, lon -> userLat = lat; userLon = lon; moveCamera(lat, lon, 18f) }
        } else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    fun refreshStores() {
        scope.launch {
            val refreshed = storeRepository.getAllStores()
            if (refreshed.isSuccess) stores = refreshed.getOrDefault(emptyList())
        }
    }

    val initialLat = savedLocation?.first ?: 30.0444
    val initialLon = savedLocation?.second ?: 31.2357
    val initialZoom = (savedLocation?.third ?: 14.0).toFloat()

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AndroidView(
                factory = { ctx ->
                    val mapView = MapView(ctx).apply { isClickable = true; setBuiltInZoomControls(false) }
                    val cache = MapLayerHelper.createTileCache(ctx, mapView)
                    val bundle = MapLayerHelper.LayerBundle(tileCache = cache)
                    layerBundle = bundle
                    MapLayerHelper.applyOnline(mapView, bundle)
                    mapView.model.mapViewPosition.setCenter(LatLong(initialLat, initialLon))
                    mapView.model.mapViewPosition.zoomLevel = initialZoom.toInt().toByte()
                    mapViewRef = mapView

                    val lastZoom = intArrayOf(-1)
                    mapView.model.mapViewPosition.addObserver {
                        val mv = mapViewRef ?: return@addObserver
                        val z = mv.model.mapViewPosition.zoomLevel.toInt()
                        if (z == lastZoom[0]) return@addObserver
                        lastZoom[0] = z
                        addMarkersToMap(ctx, mv, storesRef.value, userLatRef.value, userLonRef.value)
                    }

                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onLongPress(e: MotionEvent) {
                            if (isAddModeRef.value) {
                                val latLong = mapView.mapViewProjection.fromPixels(e.x.toDouble(), e.y.toDouble())
                                selectedLat = latLong.latitude; selectedLon = latLong.longitude; showAddDialog = true
                            }
                        }
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            val tapped = mapView.mapViewProjection.fromPixels(e.x.toDouble(), e.y.toDouble())
                            val nearest = findNearestStore(storesRef.value, tapped.latitude, tapped.longitude, 80.0)
                            if (nearest != null) { selectedStore = nearest; return true }
                            return false
                        }
                    })
                    mapView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); false }
                    mapView
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView -> mapViewRef = mapView; layerBundle?.let { MapLayerHelper.resume(it) } },
                onRelease = { mapView ->
                    try {
                        val center = mapView.model.mapViewPosition.center
                        val zoom = mapView.model.mapViewPosition.zoomLevel.toDouble()
                        kotlinx.coroutines.runBlocking {
                            appPreferences.saveLastLocation(center.latitude, center.longitude, zoom)
                        }
                    } catch (_: Exception) {
                    }
                    layerBundle?.let { MapLayerHelper.destroy(it) }
                    mapView.destroy(); mapViewRef = null; layerBundle = null
                }
            )

            SearchBar(
                query = searchQuery, onQueryChange = { searchQuery = it }, results = searchResults,
                onResultClick = { store ->
                    navResults = searchResults
                    navIndex = navResults.indexOfFirst { it.store.id == store.id }.takeIf { it >= 0 } ?: 0
                    moveCamera(store.latitude, store.longitude, 17f)
                },
                expanded = searchExpanded, onExpandedChange = { searchExpanded = it },
                filterType = filterType, filterSub = filterSub,
                onFilterTypeChange = { type ->
                    filterType = type
                    filterSub = "الكل"
                    scope.launch { appPreferences.saveFilter(type, "الكل") }
                },
                onFilterSubChange = { sub ->
                    filterSub = sub
                    scope.launch { appPreferences.saveFilter(filterType, sub) }
                },
                onOpenFilterDialog = { showFilterDialog = true },
                recentSearches = recentSearches.take(recentSearchLimit).takeIf { recentSearchLimit > 0 } ?: emptyList(),
                onRecentClick = { q ->
                    searchQuery = q
                    scope.launch { appPreferences.addRecentSearch(q) }
                },
                onSearchCommit = { q ->
                    scope.launch { appPreferences.addRecentSearch(q) }
                },
                modifier = Modifier.align(Alignment.TopCenter).zIndex(if (searchExpanded) 3f else 1f)
            )

            if (showFilterDialog) {
                FilterDialog(
                    initialType = filterType, initialSub = filterSub,
                    onDismiss = { showFilterDialog = false },
                    onApply = { type, sub ->
                        filterType = type; filterSub = sub; showFilterDialog = false
                        scope.launch { appPreferences.saveFilter(type, sub) }
                    },
                    onReset = {
                        filterType = "الكل"; filterSub = "الكل"
                        scope.launch { appPreferences.saveFilter("الكل", "الكل") }
                    }
                )
            }

            if (!searchExpanded) {
                // في الوضع المظلم: خلفية سوداء + أيقونة بلون التمييز
                val overlayContainer = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
                    Color.Black else MaterialTheme.colorScheme.secondaryContainer
                val overlayContent = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer
                FloatingActionButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 8.dp, start = 16.dp).size(48.dp).zIndex(1f),
                    containerColor = overlayContainer,
                    contentColor = overlayContent,
                    shape = CircleShape
                ) { Icon(Icons.Default.Settings, contentDescription = "الإعدادات") }
            }

            if (navResults.size > 1 && navIndex in navResults.indices) {
                val current = navResults[navIndex]
                SearchResultNav(
                    currentIndex = navIndex, total = navResults.size,
                    storeName = "${current.store.category} ${current.store.name}".trim(),
                    distanceText = if (current.distanceMeters >= 0) formatDistance(current.distanceMeters) else null,
                    onPrevious = { goToNavResult(navIndex - 1) }, onNext = { goToNavResult(navIndex + 1) },
                    onDismiss = { navResults = emptyList(); navIndex = -1 },
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 100.dp).zIndex(2f)
                )
            }

            AnimatedVisibility(
                visible = isAddMode,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
            ) {
                Text(
                    text = "اضغط مطولاً على أي مكان في الخريطة لإضافة موقع",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(12.dp)).padding(16.dp)
                )
            }

            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(visible = isMenuExpanded, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FloatingActionButton(
                            onClick = { requestLocationAndMove(); isMenuExpanded = false },
                            containerColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
                                Color.Black else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = CircleShape, modifier = Modifier.size(48.dp)
                        ) { Icon(Icons.Default.LocationOn, contentDescription = "موقعي الحالي") }
                        FloatingActionButton(
                            onClick = { isAddMode = !isAddMode; isMenuExpanded = false },
                            containerColor = if (isAddMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = if (isAddMode) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = CircleShape, modifier = Modifier.size(48.dp)
                        ) { Icon(if (isAddMode) Icons.Default.Close else Icons.Default.Add, contentDescription = null) }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .combinedClickable(
                            onClick = { isMenuExpanded = !isMenuExpanded },
                            onLongClick = {
                                requestLocationAndMove()
                                isMenuExpanded = false
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            if (showAddDialog) {
                AddStoreDialog(
                    latitude = selectedLat, longitude = selectedLon,
                    onDismiss = { showAddDialog = false },
                    onSave = { name, categoryPath, description ->
                        scope.launch {
                            val store = Store(name = name, category = categoryPath, description = description, latitude = selectedLat, longitude = selectedLon)
                            val result = storeRepository.addStore(store)
                            if (result.isSuccess) {
                                Toast.makeText(context, "تم حفظ الموقع بنجاح", Toast.LENGTH_SHORT).show()
                                refreshStores(); showAddDialog = false; isAddMode = false
                            } else Toast.makeText(context, "فشل الحفظ: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }

            storeToEdit?.let { store ->
                AddStoreDialog(
                    latitude = store.latitude, longitude = store.longitude, initialStore = store,
                    onDismiss = { storeToEdit = null },
                    onSave = { name, categoryPath, description ->
                        scope.launch {
                            val updated = store.copy(name = name, category = categoryPath, description = description)
                            val result = storeRepository.updateStore(updated)
                            if (result.isSuccess) {
                                Toast.makeText(context, "تم التحديث", Toast.LENGTH_SHORT).show()
                                refreshStores(); storeToEdit = null; selectedStore = null
                            } else Toast.makeText(context, "فشل التحديث", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }

            selectedStore?.let { store ->
                StoreDetailsDialog(
                    store = store,
                    onDismiss = { selectedStore = null },
                    onEdit = { selectedStore = null; storeToEdit = it },
                    onDelete = {
                        scope.launch {
                            val result = storeRepository.deleteStore(it.id)
                            if (result.isSuccess) {
                                Toast.makeText(context, "تم الحذف", Toast.LENGTH_SHORT).show()
                                val deletedId = it.id
                                refreshStores(); selectedStore = null
                                navResults = navResults.filter { item -> item.store.id != deletedId }
                                if (navResults.isEmpty()) navIndex = -1 else navIndex = navIndex.coerceIn(0, navResults.lastIndex)
                            } else Toast.makeText(context, "فشل الحذف", Toast.LENGTH_LONG).show()
                        }
                    }
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
        if (dist < minDist && dist <= maxDistanceMeters) { minDist = dist; nearest = store }
    }
    return nearest
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun addMarkersToMap(context: Context, mapView: MapView, stores: List<Store>, userLat: Double?, userLon: Double?) {
    try {
        mapView.layerManager.layers.filterIsInstance<Marker>().forEach { mapView.layerManager.layers.remove(it) }

        val zoom = mapView.model.mapViewPosition.zoomLevel.toInt()
        val centerLat = mapView.model.mapViewPosition.center.latitude
        val mode = MarkerIconHelper.displayModeForZoom(zoom, centerLat)

        if (mode != MarkerIconHelper.DisplayMode.HIDDEN) {
            stores.forEach { store ->
                try {
                    val bitmap = MarkerIconHelper.getMarkerBitmap(store.category, mode) ?: return@forEach
                    mapView.layerManager.layers.add(
                        Marker(LatLong(store.latitude, store.longitude), bitmap, 0, -bitmap.height / 2)
                    )
                } catch (_: Exception) {}
            }
        }

        if (userLat != null && userLon != null) {
            try {
                val userBmp = MarkerIconHelper.getUserLocationBitmap(mode)
                mapView.layerManager.layers.add(
                    Marker(LatLong(userLat, userLon), userBmp, 0, -userBmp.height / 2)
                )
            } catch (_: Exception) {}
        }
    } catch (_: Exception) {}
}

@SuppressLint("MissingPermission")
private fun moveToCurrentLocation(context: Context, mapView: MapView?, onLocation: ((Double, Double) -> Unit)? = null) {
    LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
        .addOnSuccessListener { location ->
            if (location != null) {
                mapView?.model?.mapViewPosition?.animateTo(LatLong(location.latitude, location.longitude))
                mapView?.model?.mapViewPosition?.zoomLevel = 16.toByte()
                onLocation?.invoke(location.latitude, location.longitude)
                Toast.makeText(context, "تم تحديد موقعك الحالي", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(context, "تعذر الحصول على الموقع، تأكد من تفعيل GPS", Toast.LENGTH_LONG).show()
        }
        .addOnFailureListener { Toast.makeText(context, "حدث خطأ أثناء تحديد الموقع", Toast.LENGTH_LONG).show() }
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
