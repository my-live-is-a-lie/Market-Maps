package com.marketmaps.app.ui.map

import android.Manifest
import android.view.View
import android.content.res.Configuration
import android.content.ComponentCallbacks2
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.marketmaps.app.data.FILTER_ALL
import com.marketmaps.app.data.AppPreferences
import com.marketmaps.app.data.SavedLocation
import com.marketmaps.app.data.AppSettings
import com.marketmaps.app.data.MapDownloader
import com.marketmaps.app.data.MapProvider
import com.marketmaps.app.data.DrawerSide
import com.marketmaps.app.data.EdgeSwipeSide
import com.marketmaps.app.data.Store
import com.marketmaps.app.data.StoreRepository
import com.marketmaps.app.data.StorePhotoUploader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.marketmaps.app.util.logged
import java.util.concurrent.atomic.AtomicInteger
import org.mapsforge.map.layer.Layer
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import com.marketmaps.app.util.logW

@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenFullSettings: () -> Unit = onOpenSettings,
    /** شاشة أخرى (الإعدادات) تغطي الخريطة بالكامل: نوقف تحميل البلاطات والرسم */
    isCovered: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val storeRepository = remember { StoreRepository() }
    val appPreferences = remember { AppPreferences(context) }
    // ننتظر أول قراءة من DataStore حتى لا نفتح على القاهرة ونحفظها بالخطأ
    var locationReady by remember { mutableStateOf(false) }
    var savedLocation by remember { mutableStateOf<SavedLocation?>(null) }
    LaunchedEffect(Unit) {
        savedLocation = appPreferences.lastLocation.first()
        locationReady = true
    }
    // Flow واحد لكل الإعدادات بدل 10 Flows منفصلة (يُصدر فقط عند تغيّر قيمة فعلاً)
    val settings by appPreferences.settings.collectAsState(initial = AppSettings())
    val offlineMode = settings.offlineMode
    val activeMapFileName = settings.mapFileName
    val recentSearches = settings.recentSearches
    val recentSearchLimit = settings.recentSearchLimit
    val mapProvider = settings.mapProvider


    val showMarkerLabels = settings.showMarkerLabels
    val drawerSide = settings.drawerSide
    val edgeSwipeEnabled = settings.edgeSwipeEnabled
    val edgeSwipeSide = settings.edgeSwipeSide
    val edgeSwipeSensitivity = settings.edgeSwipeSensitivity
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
    var selectedLat by remember { mutableDoubleStateOf(0.0) }
    var selectedLon by remember { mutableDoubleStateOf(0.0) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var stores by remember { mutableStateOf<List<Store>>(emptyList()) }
    // rememberSaveable: نص البحث والفلاتر تبقى بعد تدوير الشاشة أو إغلاق النظام للتطبيق في الخلفية
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var filterType by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var filterSub by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var filtersInitialized by rememberSaveable { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLon by remember { mutableStateOf<Double?>(null) }
    var selectedStore by remember { mutableStateOf<Store?>(null) }
    var storeToEdit by remember { mutableStateOf<Store?>(null) }
    var navResults by remember { mutableStateOf<List<StoreWithDistance>>(emptyList()) }
    var navIndex by remember { mutableIntStateOf(-1) }
    // تمييز أيقونة نتيجة البحث أو المحل المفتوح في البطاقة السفلية
    val highlightedStoreId = when {
        selectedStore != null -> selectedStore!!.id
        navResults.size > 1 && navIndex in navResults.indices -> navResults[navIndex].store.id
        else -> null
    }
    // تأثير تمييز سريع
    val highlightScaleAnim = remember { Animatable(1f) }
    LaunchedEffect(highlightedStoreId) {
        if (highlightedStoreId != null) {
            highlightScaleAnim.snapTo(1f)
            highlightScaleAnim.animateTo(
                1.14f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 900f)
            )
        } else {
            highlightScaleAnim.snapTo(1f)
        }
    }
    // قراءة highlightScaleAnim.value مباشرة في جسم MapScreen كانت تعيد تركيب الشاشة
    // كلها (+1000 سطر) مع كل إطار أنيميشن. derivedStateOf لا يُبلغ إلا عند تغيّر
    // القيمة المقرّبة: خطوات 1/8 لعلامات Mapsforge و 1/25 لأيقونات جوجل.
    val highlightScaleBucket by remember {
        derivedStateOf { (highlightScaleAnim.value * 8f).toInt() / 8f }
    }
    val highlightScale by remember {
        derivedStateOf { (highlightScaleAnim.value * 25f).toInt() / 25f }
    }
    var cameraTarget by remember { mutableStateOf<CameraTarget?>(null) }
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
                stiffness = 700f
            )
        )
    }
    // إزاحة الأزرار فوق البطاقة تُقرأ في مرحلة التخطيط فقط (offset { }) بدل
    // حساب padding في جسم الدالة، فلا تُعاد الشاشة كلها مع كل إطار من حركة البطاقة.
    val cardLiftOffset: Density.() -> IntOffset = {
        IntOffset(0, -cardLiftAnim.value.coerceAtLeast(0f).roundToInt())
    }

    // النصوص المطبَّعة تُحسب مرة واحدة لكل تغيّر في قائمة المحلات
    val searchIndex = remember(stores) { buildSearchIndex(stores) }
    // تأخير البحث 150ms أثناء الكتابة (المسح فوري) بدل تصفية كل المحلات مع كل حرف
    var debouncedQuery by remember { mutableStateOf(searchQuery) }
    LaunchedEffect(Unit) {
        snapshotFlow { searchQuery }
            .debounce { q -> if (q.isBlank()) 0L else 150L }
            .collect { debouncedQuery = it }
    }
    val searchResults = remember(debouncedQuery, searchIndex, userLat, userLon, filterType, filterSub) {
        filterAndSortStores(searchIndex, debouncedQuery, userLat, userLon, filterType, filterSub)
    }

    fun moveCamera(lat: Double, lon: Double, zoom: Float = 17f) {
        cameraTarget = CameraTarget(lat, lon, zoom)
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
    val selectedStoreRef = remember { mutableStateOf(selectedStore) }
    selectedStoreRef.value = selectedStore
    // مستوى تكبير Mapsforge كحالة Compose (يُحدَّث من المراقب عبر الخيط الرئيسي)
    var mapsforgeZoom by remember { mutableIntStateOf(-1) }

    fun saveCameraPosition() {
        val mv = mapViewRef ?: return
        try {
            val center = mv.model.mapViewPosition.center
            val zoom = mv.model.mapViewPosition.zoomLevel.toDouble()
            scope.launch {
                appPreferences.saveLastLocation(center.latitude, center.longitude, zoom)
            }
        } catch (e: Exception) {
            logW(TAG, "حفظ موقع الكاميرا", e)
        }
    }

    val isCoveredState = rememberUpdatedState(isCovered)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    if (!isCoveredState.value) layerBundle?.let { MapLayerHelper.resume(it) }
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

    // الخريطة مخفية خلف الإعدادات: إيقاف خيوط تحميل البلاطات + إخفاء الـ View حتى لا
    // يُعاد رسمه، ثم الاستئناف عند العودة (ثالثاً/6).
    LaunchedEffect(isCovered, layerBundle, mapViewRef) {
        val bundle = layerBundle
        if (isCovered) {
            bundle?.let { MapLayerHelper.pause(it) }
            mapViewRef?.visibility = View.INVISIBLE
        } else {
            mapViewRef?.visibility = View.VISIBLE
            if (bundle != null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                MapLayerHelper.resume(bundle)
            }
        }
    }

    // تفريغ بلاطات الذاكرة عند خروج التطبيق للخلفية (ثالثاً/5). أيقونات العلامات
    // تُفرَّغ في MarketMapsApp.onTrimMemory.
    DisposableEffect(Unit) {
        val appContext = context.applicationContext
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                    layerBundle?.let { MapLayerHelper.trimMemory(it) }
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        }
        appContext.registerComponentCallbacks(callbacks)
        onDispose { appContext.unregisterComponentCallbacks(callbacks) }
    }

    LaunchedEffect(Unit) {
        // الفلاتر أولاً (قراءة محلية سريعة) بدل انتظار تحميل المحلات من الشبكة،
        // ومرة واحدة فقط: بعد تدوير الشاشة تبقى القيم المستعادة من rememberSaveable.
        if (!filtersInitialized) {
            if (appPreferences.rememberFilter.first()) {
                filterType = appPreferences.savedFilterType.first()
                filterSub = appPreferences.savedFilterSub.first()
            }
            filtersInitialized = true
        }
        tryGetLastLocation(context) { lat, lon -> userLat = lat; userLon = lon }
    }

    // مزامنة حية: أي إضافة/تعديل/حذف من أي جهاز يظهر فوراً
    LaunchedEffect(storeRepository) {
        var firstEmission = true
        storeRepository.observeStores().collect { list ->
            stores = list
            if (firstEmission) {
                firstEmission = false
                if (list.isEmpty()) {
                    // لا نزعج المستخدم فوراً — قد تصل بيانات السيرفر بعد الكاش الفارغ
                }
            }
        }
    }

    // لا حاجة لنقل Mapsforge للموقع المحفوظ بعد إنشائها: الخريطة لا تُنشأ إلا بعد
    // قراءة الموقع (locationReady)، فالموضع الأولي في factory صحيح من البداية.

    LaunchedEffect(offlineMode, mapViewRef, mapProvider, activeMapFileName) {
        if (mapProvider != MapProvider.MAPSFORGE) return@LaunchedEffect
        val mapView = mapViewRef ?: return@LaunchedEffect
        val bundle = layerBundle ?: return@LaunchedEffect
        if (offlineMode) {
            val preferred = activeMapFileName?.takeIf { MapDownloader.isDownloaded(context, it) }
            val anyFile = preferred
                ?: MapDownloader.listDownloaded(context).firstOrNull()?.fileName
            if (anyFile != null) {
                MapLayerHelper.applyOffline(context, mapView, bundle, anyFile)
            } else {
                Toast.makeText(context, "لا توجد خريطة محمّلة. حمّلها من الإعدادات.", Toast.LENGTH_LONG).show()
                MapLayerHelper.applyOnline(mapView, bundle)
            }
        } else {
            MapLayerHelper.applyOnline(mapView, bundle)
        }
        // تبديل الوضع يتم عادة من الإعدادات والخريطة مغطاة: applyOnline يشغّل الطبقة
        // الجديدة، فنوقفها حتى العودة إلى الخريطة.
        if (isCoveredState.value) MapLayerHelper.pause(bundle)
        // لا حاجة لإعادة بناء العلامات هنا: تبديل طبقة الأساس لا يلمس الـ Markers
    }

    // mapsforgeZoom جزء من المفاتيح: تغيّر مستوى التكبير يعيد بناء العلامات بالحجم
    // الجديد مع الإبقاء على العلامة المميزة (كان مراقب التكبير يمرر null فيضيع التمييز).
    // LaunchedEffect يلغي أي بناء سابق لم يكتمل عند تغيّر أي مفتاح.
    LaunchedEffect(mapViewRef, stores, userLat, userLon, mapProvider, highlightedStoreId, highlightScaleBucket, mapsforgeZoom) {
        if (mapProvider != MapProvider.MAPSFORGE) return@LaunchedEffect
        val mapView = mapViewRef ?: return@LaunchedEffect
        updateMapsforgeMarkers(mapView, stores, userLat, userLon, highlightedStoreId, highlightScaleBucket)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            moveToCurrentLocation(context) { lat, lon -> userLat = lat; userLon = lon; moveCamera(lat, lon, 18f) }
        } else Toast.makeText(context, "يجب السماح بالوصول إلى الموقع", Toast.LENGTH_LONG).show()
    }

    fun requestLocationAndMove() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            moveToCurrentLocation(context) { lat, lon -> userLat = lat; userLon = lon; moveCamera(lat, lon, 18f) }
        } else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // المزامنة الحية تحدّث القائمة تلقائياً؛ هذه للدفع اليدوي إن لزم
    fun refreshStores() {
        scope.launch {
            val refreshed = storeRepository.getAllStores()
            if (refreshed.isSuccess) stores = refreshed.getOrDefault(emptyList())
        }
    }

    val initialLat = savedLocation?.lat ?: 30.0444
    val initialLon = savedLocation?.lon ?: 31.2357
    val initialZoom = (savedLocation?.zoom ?: 14.0).toFloat()


    // زر الرجوع يغلق الطبقات المفتوحة أولاً بدل الخروج من التطبيق
    BackHandler(enabled = sideMenuOpen || searchExpanded || selectedStore != null || isAddMode || isMenuExpanded || showAddDialog || showFilterDialog) {
        when {
            showFilterDialog -> showFilterDialog = false
            showAddDialog -> showAddDialog = false
            sideMenuOpen -> sideMenuOpen = false
            selectedStore != null -> { selectedStore = null; detailsCardHeightPx = 0 }
            isAddMode -> isAddMode = false
            searchExpanded -> searchExpanded = false
            isMenuExpanded -> isMenuExpanded = false
        }
    }

    if (!locationReady) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

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
                    highlightedStoreId = highlightedStoreId,
                    highlightScale = highlightScale,
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
                        // لا نضبط userScaleFactor: Mapsforge يضرب مسبقاً في كثافة الشاشة
                        // (deviceScaleFactor = density). الضرب في density مرة ثانية كان يضخم
                        // البلاطة إلى 512–1024px ويرفع ذاكرة البلاطات إلى >130MB.
                        // وضوح نصوص الأوفلاين يُعالج عبر textScale في MapLayerHelper.
                    }
                    val bundle = MapLayerHelper.createBundle(ctx, mapView)
                    layerBundle = bundle
                    MapLayerHelper.applyOnline(mapView, bundle)
                    mapView.model.mapViewPosition.setCenter(LatLong(initialLat, initialLon))
                    mapView.model.mapViewPosition.zoomLevel = initialZoom.toInt().toByte()
                    mapViewRef = mapView

                    // المراقب يُستدعى أيضاً من خيط أنيميشن Mapsforge (كل 15ms أثناء الحركة)،
                    // لذلك نكتفي بمقارنة رخيصة هنا ونمرر التغيير للخيط الرئيسي عبر post.
                    val lastZoom = AtomicInteger(-1)
                    // مزامنة فورية حتى تُرسم العلامات من أول إطار (لا ننتظر تغيّر التكبير)
                    mapView.post {
                        val z = mapView.model.mapViewPosition.zoomLevel.toInt()
                        lastZoom.set(z)
                        mapsforgeZoom = z
                    }
                    mapView.model.mapViewPosition.addObserver {
                        val z = mapView.model.mapViewPosition.zoomLevel.toInt()
                        if (lastZoom.getAndSet(z) == z) return@addObserver
                        mapView.post { mapsforgeZoom = z }
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
                update = { mapView ->
                    mapViewRef = mapView
                    if (!isCoveredState.value) layerBundle?.let { MapLayerHelper.resume(it) }
                },
                onRelease = { mapView ->
                    // الموقع يُحفظ مسبقاً في ON_PAUSE و onDispose — لا تستخدم runBlocking على الخيط الرئيسي
                    val bundle = layerBundle
                    if (bundle != null) MapLayerHelper.destroy(mapView, bundle) else mapView.destroy()
                    mapViewRef = null; layerBundle = null
                }
            )

            } // end Mapsforge

            SearchBar(
                query = searchQuery, onQueryChange = { searchQuery = it }, results = searchResults,
                onResultClick = { store ->
                    // أعد حساب المسافات من موقع المستخدم الحالي لكل نتيجة
                    navResults = if (userLat != null && userLon != null) {
                        searchResults.map { item ->
                            item.copy(
                                distanceMeters = haversineMeters(
                                    userLat!!, userLon!!,
                                    item.store.latitude, item.store.longitude
                                )
                            )
                        }.sortedBy { if (it.distanceMeters >= 0) it.distanceMeters else Double.MAX_VALUE }
                    } else {
                        searchResults
                    }
                    navIndex = navResults.indexOfFirst { it.store.id == store.id }.takeIf { it >= 0 } ?: 0
                    moveCamera(store.latitude, store.longitude, 17f)
                },
                expanded = searchExpanded, onExpandedChange = { searchExpanded = it },
                filterType = filterType, filterSub = filterSub,
                onFilterTypeChange = { type ->
                    filterType = type
                    filterSub = FILTER_ALL
                    scope.launch { appPreferences.saveFilter(type, FILTER_ALL) }
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
                modifier = Modifier.align(Alignment.TopCenter).zIndex(if (searchExpanded) 16f else 14f)
            )


            // نسب OpenStreetMap (مطلوب بترخيص ODbL) — يظهر مع Mapsforge
            if (mapProvider == MapProvider.MAPSFORGE) {
                Text(
                    text = "© OpenStreetMap contributors",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp)
                        .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .zIndex(2f)
                )
            }

            if (showFilterDialog) {
                FilterDialog(
                    initialType = filterType, initialSub = filterSub,
                    onDismiss = { showFilterDialog = false },
                    onApply = { type, sub ->
                        filterType = type; filterSub = sub; showFilterDialog = false
                        scope.launch { appPreferences.saveFilter(type, sub) }
                    },
                    onReset = {
                        filterType = FILTER_ALL; filterSub = FILTER_ALL
                        scope.launch { appPreferences.saveFilter(FILTER_ALL, FILTER_ALL) }
                    }
                )
            }


            if (navResults.size > 1 && navIndex in navResults.indices) {
                val current = navResults[navIndex]
                // حساب المسافة لحظياً من موقع المستخدم لكل نتيجة (لا نعتمد على قيمة قديمة مشتركة)
                val liveDist = if (userLat != null && userLon != null) {
                    haversineMeters(userLat!!, userLon!!, current.store.latitude, current.store.longitude)
                } else {
                    current.distanceMeters
                }
                SearchResultNav(
                    currentIndex = navIndex,
                    total = navResults.size,
                    storeName = "${current.store.category} ${current.store.name}".trim(),
                    distanceText = if (liveDist >= 0) formatDistance(liveDist) else null,
                    onPrevious = { goToNavResult(navIndex - 1) },
                    onNext = { goToNavResult(navIndex + 1) },
                    onDismiss = { navResults = emptyList(); navIndex = -1 },
                    // AbsoluteAlignment.BottomLeft = اليسار الفعلي دائماً (لا ينعكس مع العربية)
                    modifier = Modifier
                        .align(AbsoluteAlignment.BottomLeft)
                        .absolutePadding(left = 12.dp, bottom = if (detailsCardVisible) 8.dp else 16.dp)
                        .offset(cardLiftOffset)
                        .zIndex(3f)
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
                    .padding(start = 16.dp, bottom = 16.dp, end = 16.dp)
                    .offset(cardLiftOffset)
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
                    onSave = { name, categoryPath, description, newPhotoUris, existingPhotoUrls, onComplete ->
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
                                val id = result.getOrNull().orEmpty()
                                if (newPhotoUris.isNotEmpty() && id.isNotBlank()) {
                                    val urls = StorePhotoUploader(context).upload(id, newPhotoUris)
                                    if (urls.isNotEmpty()) {
                                        storeRepository.setPhotoUrls(id, urls)
                                    }
                                }
                                Toast.makeText(context, "تم حفظ الموقع بنجاح", Toast.LENGTH_SHORT).show()
                                showAddDialog = false; isAddMode = false
                                onComplete(true)
                            } else {
                                Toast.makeText(context, "فشل الحفظ: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                onComplete(false)
                            }
                        }
                    }
                )
            }

            storeToEdit?.let { store ->
                AddStoreDialog(
                    latitude = store.latitude, longitude = store.longitude, initialStore = store,
                    onDismiss = { storeToEdit = null },
                    onSave = { name, categoryPath, description, newPhotoUris, existingPhotoUrls, onComplete ->
                        scope.launch {
                            var photos = existingPhotoUrls
                            if (newPhotoUris.isNotEmpty()) {
                                val uploaded = StorePhotoUploader(context).upload(store.id, newPhotoUris)
                                photos = (photos + uploaded).distinct().take(3)
                            }
                            val updated = store.copy(
                                name = name,
                                category = categoryPath,
                                description = description,
                                photoUrls = photos
                            )
                            val result = storeRepository.updateStore(updated)
                            if (result.isSuccess) {
                                Toast.makeText(context, "تم التحديث", Toast.LENGTH_SHORT).show()
                                storeToEdit = null
                                selectedStore = null
                                detailsCardHeightPx = 0
                                selectedStore = updated
                                onComplete(true)
                            } else {
                                Toast.makeText(context, "فشل التحديث", Toast.LENGTH_LONG).show()
                                onComplete(false)
                            }
                        }
                    }
                )
            }

            selectedStore?.let { store ->
                val lat = userLat ?: savedLocation?.lat
                val lon = userLon ?: savedLocation?.lon
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

private const val TAG = "MapScreen"

/**
 * تحديث علامات Mapsforge:
 * - صور الأيقونات تُجهَّز على Dispatchers.Default (كان تحليل SVG والرسم على الخيط
 *   الرئيسي فيتقطع التكبير عند تجاوز 12 و14 و16).
 * - الاستبدال دفعة واحدة: removeAll + addAll بدون إعادة رسم ثم redraw واحدة.
 *   Layers في Mapsforge يستخدم CopyOnWriteArrayList، فكان كل add/remove منفرد ينسخ
 *   المصفوفة كاملة ويطلب إعادة رسم (O(N²) مع كثرة المحلات).
 * - لا نستدعي onDestroy على العلامات القديمة: صورها مشتركة من كاش الأيقونات.
 */
private suspend fun updateMapsforgeMarkers(
    mapView: MapView,
    stores: List<Store>,
    userLat: Double?,
    userLon: Double?,
    highlightedStoreId: String?,
    highlightScale: Float
) {
    val zoom = mapView.model.mapViewPosition.zoomLevel.toInt()
    val newMarkers = withContext(Dispatchers.Default) {
        buildMapsforgeMarkers(stores, zoom, userLat, userLon, highlightedStoreId, highlightScale)
    }
    val layers = mapView.layerManager.layers
    val oldMarkers = layers.filterIsInstance<Marker>()
    if (oldMarkers.isNotEmpty()) layers.removeAll(oldMarkers, false)
    if (newMarkers.isNotEmpty()) layers.addAll(newMarkers, false)
    mapView.layerManager.redrawLayers()
}

private fun buildMapsforgeMarkers(
    stores: List<Store>,
    zoom: Int,
    userLat: Double?,
    userLon: Double?,
    highlightedStoreId: String?,
    highlightScale: Float
): List<Layer> {
    val out = ArrayList<Layer>(stores.size + 1)
    val mode = MarkerIconHelper.displayModeForZoom(zoom)
    val sizePx = MarkerIconHelper.markerSizePxForZoom(zoom)

    if (mode != MarkerIconHelper.DisplayMode.HIDDEN && sizePx > 0) {
        var highlightedMarker: Marker? = null
        for (store in stores) {
            val highlighted = highlightedStoreId != null && store.id == highlightedStoreId
            // المميز يُرسم بحجمه المكبَّر مباشرة من الـ SVG (بدل createScaledBitmap)
            val size = if (highlighted) (sizePx * highlightScale).roundToInt() else sizePx
            val bitmap = logged(TAG, "أيقونة ${store.category}") {
                MarkerIconHelper.getMarkerBitmap(store.category, mode, size)
            } ?: continue
            val marker = Marker(LatLong(store.latitude, store.longitude), bitmap, 0, -bitmap.height / 2)
            if (highlighted) highlightedMarker = marker else out.add(marker)
        }
        // المميز آخراً حتى يُرسم فوق البقية
        highlightedMarker?.let { out.add(it) }
    }

    if (userLat != null && userLon != null) {
        logged(TAG, "أيقونة الموقع الحالي") {
            val userBmp = MarkerIconHelper.getUserLocationBitmap(MarkerIconHelper.DisplayMode.BUBBLE_MEDIUM)
            out.add(Marker(LatLong(userLat, userLon), userBmp, 0, -userBmp.height / 2))
        }
    }
    return out
}

@SuppressLint("MissingPermission")
private fun moveToCurrentLocation(context: Context, onLocation: (Double, Double) -> Unit) {
    LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
        .addOnSuccessListener { location ->
            if (location != null) {
                onLocation(location.latitude, location.longitude)
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

    // حركة استقرار سلسة (Material) بدون ارتداد
    val settleSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
    // عتبة السرعة لاعتبار السحبة "سريعة" (fling)
    val flingVelocityPx = with(density) { 700.dp.toPx() }

    suspend fun settle(targetOpen: Boolean, flingVelocity: Float = 0f) {
        progress.animateTo(
            targetValue = if (targetOpen) 1f else 0f,
            animationSpec = settleSpec,
            initialVelocity = flingVelocity / panelWidthPx
        )
        onOpenChange(targetOpen)
    }

    // سرعة السحب أولاً، ثم موضع 50٪ كاحتياطي
    fun decideOpen(currentProgress: Float, velocityTowardOpen: Float): Boolean = when {
        velocityTowardOpen > flingVelocityPx -> true
        velocityTowardOpen < -flingVelocityPx -> false
        else -> currentProgress >= 0.5f
    }

    // فتح/إغلاق من الزر أو من الخارج
    LaunchedEffect(open) {
        if (dragging) return@LaunchedEffect
        val target = if (open) 1f else 0f
        if (kotlin.math.abs(progress.value - target) > 0.01f) {
            progress.animateTo(target, animationSpec = settleSpec)
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

    // شرائط السحب من الحافة مع دعم السرعة
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
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { dx ->
                            val next = (progress.value + dx / panelWidthPx).coerceIn(0f, 1f)
                            scope.launch { progress.snapTo(next) }
                        },
                        onDragStarted = {
                            dragging = true
                            activeSide = DrawerSide.LEFT
                            onEdgeSideSelected(DrawerSide.LEFT)
                        },
                        onDragStopped = { velocity ->
                            dragging = false
                            val shouldOpen = decideOpen(progress.value, velocity)
                            settle(shouldOpen, velocity)
                        }
                    )
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
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { dx ->
                            // من اليمين: السحب لليسار (dx سالب) يزيد التقدم
                            val next = (progress.value - dx / panelWidthPx).coerceIn(0f, 1f)
                            scope.launch { progress.snapTo(next) }
                        },
                        onDragStarted = {
                            dragging = true
                            activeSide = DrawerSide.RIGHT
                            onEdgeSideSelected(DrawerSide.RIGHT)
                        },
                        onDragStopped = { velocity ->
                            dragging = false
                            val shouldOpen = decideOpen(progress.value, -velocity)
                            settle(shouldOpen, -velocity)
                        }
                    )
            )
        }
    }

    if (!visible) return

    // تعتيم بدون ripple
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Color.Black.copy(alpha = 0.35f * p))
            .clickable(
                enabled = p > 0.3f,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                scope.launch { settle(false) }
            }
    )

    // AbsoluteRoundedCornerShape: لا تنعكس مع RTL — تدوير الحافة الداخلية المواجهة للخريطة فقط
    val cornerRadius = 12.dp
    val panelShape = if (activeSide == DrawerSide.RIGHT) {
        AbsoluteRoundedCornerShape(topLeft = cornerRadius, bottomLeft = cornerRadius)
    } else {
        AbsoluteRoundedCornerShape(topRight = cornerRadius, bottomRight = cornerRadius)
    }

    // اللوحة: absoluteOffset لتفادي انعكاس RTL + ظل + سحب بسرعة
    Box(
        modifier = Modifier
            .align(panelAlign)
            .fillMaxHeight()
            .fillMaxWidth(0.55f)
            .zIndex(21f)
            .absoluteOffset { IntOffset(offsetX.roundToInt(), 0) }
            .shadow(elevation = 18.dp, shape = panelShape, clip = false)
            .clip(panelShape)
            .background(Color(0xFF121212))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { dx ->
                    val delta = if (activeSide == DrawerSide.RIGHT) -dx else dx
                    val next = (progress.value + delta / panelWidthPx).coerceIn(0f, 1f)
                    scope.launch { progress.snapTo(next) }
                },
                onDragStarted = { dragging = true },
                onDragStopped = { velocity ->
                    dragging = false
                    val signedVelocity = if (activeSide == DrawerSide.RIGHT) -velocity else velocity
                    val shouldOpen = decideOpen(progress.value, signedVelocity)
                    settle(shouldOpen, signedVelocity)
                }
            )
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

