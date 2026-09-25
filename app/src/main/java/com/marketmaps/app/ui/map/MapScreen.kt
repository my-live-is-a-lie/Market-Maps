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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
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
import com.marketmaps.app.data.DrawerSide
import com.marketmaps.app.data.EdgeSwipeSide
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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenFullSettings: () -> Unit = onOpenSettings
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
    val showMarkerLabels by appPreferences.showMarkerLabels.collectAsState(initial = true)
    val drawerSide by appPreferences.drawerSide.collectAsState(initial = DrawerSide.RIGHT)
    val edgeSwipeEnabled by appPreferences.edgeSwipeEnabled.collectAsState(initial = true)
    val edgeSwipeSide by appPreferences.edgeSwipeSide.collectAsState(initial = EdgeSwipeSide.BOTH)
    val edgeSwipeSensitivity by appPreferences.edgeSwipeSensitivity.collectAsState(initial = 0.55f)
    var sideMenuOpen by remember { mutableStateOf(false) }
    // جانب عرض القائمة الحالي (منفصل عن إعداد السحب من الحافة)
    var panelSide by remember { mutableStateOf(DrawerSide.RIGHT) }

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
    var detailsCardHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val detailsCardVisible = selectedStore != null
    val cardLiftTargetPx = if (detailsCardVisible) {
        val hPx = if (detailsCardHeightPx > 0) detailsCardHeightPx.toFloat()
        else with(density) { 128.dp.toPx() }
        hPx + with(density) { 8.dp.toPx() }
    } else 0f
    val cardLiftAnim = remember { Animatable(0f) }
    LaunchedEffect(cardLiftTargetPx) {
        cardLiftAnim.animateTo(
            cardLiftTargetPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    val cardLift = with(density) { cardLiftAnim.value.coerceAtLeast(0f).toDp() }
    val fabBottomPad = (16.dp + cardLift).coerceAtLeast(0.dp)
    val navBottomPad = (if (detailsCardVisible) cardLift + 8.dp else 100.dp).coerceAtLeast(0.dp)

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
    val selectedStoreRef = remember { mutableStateOf(selectedStore) }
    userLonRef.value = userLon
    selectedStoreRef.value = selectedStore

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
            if (mapProvider == MapProvider.GOOGLE) {
                GoogleMapContent(
                    initialLat = initialLat,
                    initialLon = initialLon,
                    initialZoom = initialZoom,
                    stores = stores,
                    userLat = userLat,
                    userLon = userLon,
                    cameraTarget = cameraTarget,
                    isAddMode = isAddMode,
                    showLabels = showMarkerLabels,
                    onLongPress = { lat, lon ->
                        selectedLat = lat
                        selectedLon = lon
                        showAddDialog = true
                    },
                    onMarkerClick = { store ->
                        selectedStore = if (selectedStore?.id == store.id) null else store
                        if (selectedStore == null) detailsCardHeightPx = 0
                    },
                    onCameraIdle = { lat, lon, zoom ->
                        scope.launch {
                            appPreferences.saveLastLocation(lat, lon, zoom.toDouble())
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
            AndroidView(
                factory = { ctx ->
                    val mapView = MapView(ctx).apply {
                        isClickable = true
                        setBuiltInZoomControls(false)
                        // تكبير عناصر الرسم لتحسين وضوح التسميات (خصوصاً الأوفلاين)
                        val density = ctx.resources.displayMetrics.density
                        model.displayModel.setUserScaleFactor((density * 1.15f).coerceIn(1.0f, 2.2f))
                    }
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
                            if (nearest != null) {
                                selectedStore = if (selectedStoreRef.value?.id == nearest.id) null else nearest
                                if (selectedStore == null) detailsCardHeightPx = 0
                                return true
                            }
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

            } // end Mapsforge

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


            if (navResults.size > 1 && navIndex in navResults.indices) {
                val current = navResults[navIndex]
                SearchResultNav(
                    currentIndex = navIndex, total = navResults.size,
                    storeName = "${current.store.category} ${current.store.name}".trim(),
                    distanceText = if (current.distanceMeters >= 0) formatDistance(current.distanceMeters) else null,
                    onPrevious = { goToNavResult(navIndex - 1) }, onNext = { goToNavResult(navIndex + 1) },
                    onDismiss = { navResults = emptyList(); navIndex = -1 },
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = navBottomPad).zIndex(3f)
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
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = fabBottomPad, end = 16.dp)
                    .zIndex(15f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(visible = isMenuExpanded, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        // ضغط عادي: قائمة جانبية | ضغط مطول: الإعدادات مباشرة
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    CircleShape
                                )
                                .combinedClickable(
                                    onClick = {
                                        isMenuExpanded = false
                                        panelSide = drawerSide
                                        sideMenuOpen = true
                                    },
                                    onLongClick = {
                                        isMenuExpanded = false
                                        onOpenFullSettings()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "الإعدادات — ضغط مطول للفتح المباشر",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        FloatingActionButton(
                            onClick = { requestLocationAndMove(); isMenuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
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



            MapSideMenuOverlay(
                open = sideMenuOpen,
                panelSide = panelSide,
                edgeSwipeEnabled = edgeSwipeEnabled,
                edgeSwipeSide = edgeSwipeSide,
                edgeSwipeSensitivity = edgeSwipeSensitivity,
                onOpenChange = { open ->
                    sideMenuOpen = open
                },
                onEdgeSideSelected = { side ->
                    // عند بدء السحب من حافة: تحديد جانب الظهور فقط (بدون فتح فوري)
                    panelSide = side
                },
                onOpenFullSettings = onOpenFullSettings,
                onTogglePanelSide = {
                    val next = if (panelSide == DrawerSide.RIGHT) DrawerSide.LEFT else DrawerSide.RIGHT
                    panelSide = next
                    scope.launch { appPreferences.setDrawerSide(next) }
                }
            )

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
                                storeToEdit = null
                                // أغلق ثم أعد فتح البطاقة لعرض البيانات المحدّثة
                                selectedStore = null
                                detailsCardHeightPx = 0
                                refreshStores()
                                selectedStore = updated
                            } else Toast.makeText(context, "فشل التحديث", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }

            selectedStore?.let { store ->
                val lat = userLat ?: savedLocation?.first
                val lon = userLon ?: savedLocation?.second
                val dist = if (lat != null && lon != null) {
                    haversineMeters(lat, lon, store.latitude, store.longitude)
                } else null
                StoreDetailsBottomCard(
                    store = store,
                    distanceMeters = dist,
                    onDismiss = { selectedStore = null; detailsCardHeightPx = 0 },
                    onEdit = { storeToEdit = it },  // لا تغلق البطاقة عند فتح التعديل
                    onHeightChanged = { detailsCardHeightPx = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                        .zIndex(12f)
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


@Composable
private fun BoxScope.MapSideMenuOverlay(
    open: Boolean,
    panelSide: DrawerSide,
    edgeSwipeEnabled: Boolean,
    edgeSwipeSide: EdgeSwipeSide,
    edgeSwipeSensitivity: Float,
    onOpenChange: (Boolean) -> Unit,
    onEdgeSideSelected: (DrawerSide) -> Unit,
    onOpenFullSettings: () -> Unit,
    onTogglePanelSide: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val panelWidthPx = with(density) { (config.screenWidthDp * 0.55f).dp.toPx() }
    // 0 = مغلقة بالكامل، 1 = مفتوحة بالكامل
    val progress = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var activeSide by remember { mutableStateOf(panelSide) }
    LaunchedEffect(panelSide, open) {
        if (!dragging) activeSide = panelSide
    }

    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    suspend fun settle(targetOpen: Boolean) {
        progress.animateTo(if (targetOpen) 1f else 0f, animationSpec = springSpec)
        onOpenChange(targetOpen)
    }

    // فتح/إغلاق من الزر أو من الخارج
    LaunchedEffect(open) {
        if (dragging) return@LaunchedEffect
        val target = if (open) 1f else 0f
        if (kotlin.math.abs(progress.value - target) > 0.01f) {
            progress.animateTo(target, animationSpec = springSpec)
        }
    }

    val p by progress.asState()
    val visible = p > 0.001f
    val panelAlign = if (activeSide == DrawerSide.RIGHT) {
        AbsoluteAlignment.CenterRight
    } else {
        AbsoluteAlignment.CenterLeft
    }
    val offsetX = if (activeSide == DrawerSide.RIGHT) {
        ((1f - p) * panelWidthPx)
    } else {
        -((1f - p) * panelWidthPx)
    }

    // شرائط السحب التفاعلي من الحافة
    if (edgeSwipeEnabled && (!open || p < 1f)) {
        val edgeWidthDp = (28f + edgeSwipeSensitivity * 36f).dp
        val allowLeft = edgeSwipeSide == EdgeSwipeSide.LEFT || edgeSwipeSide == EdgeSwipeSide.BOTH
        val allowRight = edgeSwipeSide == EdgeSwipeSide.RIGHT || edgeSwipeSide == EdgeSwipeSide.BOTH
        val bottomClear = 140.dp

        if (allowLeft) {
            Box(
                modifier = Modifier
                    .align(AbsoluteAlignment.CenterLeft)
                    .fillMaxHeight()
                    .padding(bottom = bottomClear)
                    .width(edgeWidthDp)
                    .zIndex(5f)
                    .pointerInput(panelWidthPx, edgeSwipeSensitivity) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragging = true
                                activeSide = DrawerSide.LEFT
                                onEdgeSideSelected(DrawerSide.LEFT)
                            },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                val next = (progress.value + dx / panelWidthPx).coerceIn(0f, 1f)
                                scope.launch { progress.snapTo(next) }
                            },
                            onDragEnd = {
                                dragging = false
                                val shouldOpen = progress.value >= 0.30f
                                scope.launch { settle(shouldOpen) }
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch { settle(progress.value >= 0.30f) }
                            }
                        )
                    }
            )
        }
        if (allowRight) {
            Box(
                modifier = Modifier
                    .align(AbsoluteAlignment.CenterRight)
                    .fillMaxHeight()
                    .padding(bottom = bottomClear)
                    .width(edgeWidthDp)
                    .zIndex(5f)
                    .pointerInput(panelWidthPx, edgeSwipeSensitivity) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragging = true
                                activeSide = DrawerSide.RIGHT
                                onEdgeSideSelected(DrawerSide.RIGHT)
                            },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                // من اليمين: السحب لليسار (dx سالب) يزيد التقدم
                                val next = (progress.value - dx / panelWidthPx).coerceIn(0f, 1f)
                                scope.launch { progress.snapTo(next) }
                            },
                            onDragEnd = {
                                dragging = false
                                val shouldOpen = progress.value >= 0.30f
                                scope.launch { settle(shouldOpen) }
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch { settle(progress.value >= 0.30f) }
                            }
                        )
                    }
            )
        }
    }

    if (!visible) return

    // تعتيم يتناسب مع مدى الفتح
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Color.Black.copy(alpha = 0.35f * p))
            .clickable(enabled = p > 0.3f) {
                scope.launch { settle(false) }
            }
    )

    // اللوحة تتبع الإصبع / الحركة النابضية
    Box(
        modifier = Modifier
            .align(panelAlign)
            .fillMaxHeight()
            .fillMaxWidth(0.55f)
            .zIndex(21f)
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .background(Color(0xFF121212))
            .pointerInput(activeSide, panelWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        val delta = if (activeSide == DrawerSide.RIGHT) -dx else dx
                        val next = (progress.value + delta / panelWidthPx).coerceIn(0f, 1f)
                        scope.launch { progress.snapTo(next) }
                    },
                    onDragEnd = {
                        dragging = false
                        scope.launch { settle(progress.value >= 0.30f) }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { settle(progress.value >= 0.30f) }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeSide == DrawerSide.LEFT) {
                    IconButton(onClick = onTogglePanelSide) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "نقل القائمة لليمين",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(onClick = {
                        scope.launch { settle(false) }
                        onOpenFullSettings()
                    }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    IconButton(onClick = {
                        scope.launch { settle(false) }
                        onOpenFullSettings()
                    }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = onTogglePanelSide) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft,
                            contentDescription = "نقل القائمة لليسار",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}




