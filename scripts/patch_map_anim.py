from pathlib import Path

# ─── MapScreen.kt ───
p = Path("app/src/main/java/com/marketmaps/app/ui/map/MapScreen.kt")
c = p.read_text(encoding="utf-8")

old_highlight = """    // تأثير نابض: تصغير خفيف ثم تكبير 20٪
    val highlightScaleAnim = remember { Animatable(1f) }
    LaunchedEffect(highlightedStoreId) {
        if (highlightedStoreId != null) {
            // حركة ناعمة وبطيئة قليلاً: تصغير خفيف جداً ثم تكبير هادئ
            highlightScaleAnim.snapTo(1f)
            highlightScaleAnim.animateTo(
                0.94f,
                animationSpec = tween(durationMillis = 140)
            )
            highlightScaleAnim.animateTo(
                1.18f,
                animationSpec = spring(
                    dampingRatio = 0.78f, // أقل اهتزازاً من MediumBouncy
                    stiffness = 280f       // أبطأ من الافتراضي
                )
            )
        } else {
            highlightScaleAnim.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = 0.90f,
                    stiffness = 320f
                )
            )
        }
    }
    val highlightScale by highlightScaleAnim.asState()"""

new_highlight = """    // تأثير تمييز سريع
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
    val highlightScale by highlightScaleAnim.asState()
    // تقليل إعادة رسم العلامات أثناء الحركة (يمنع وميض موقعك)
    val highlightScaleBucket = ((highlightScale * 8f).toInt() / 8f)"""

if old_highlight not in c:
    raise SystemExit("highlight block not found")
c = c.replace(old_highlight, new_highlight, 1)

c = c.replace(
    "LaunchedEffect(mapViewRef, stores, userLat, userLon, mapProvider, highlightedStoreId, highlightScale)",
    "LaunchedEffect(mapViewRef, stores, userLat, userLon, mapProvider, highlightedStoreId, highlightScaleBucket)",
    1,
)
c = c.replace(
    "mapViewRef?.let { addMarkersToMap(context, it, stores, userLat, userLon, highlightedStoreId, highlightScale) }",
    "mapViewRef?.let { addMarkersToMap(context, it, stores, userLat, userLon, highlightedStoreId, highlightScaleBucket) }",
    1,
)

old_lift = """    LaunchedEffect(cardLiftTargetPx) {
        cardLiftAnim.animateTo(
            cardLiftTargetPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }"""
new_lift = """    LaunchedEffect(cardLiftTargetPx) {
        cardLiftAnim.animateTo(
            cardLiftTargetPx,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 700f
            )
        )
    }"""
if old_lift not in c:
    raise SystemExit("cardLift block not found")
c = c.replace(old_lift, new_lift, 1)

old_user = """        if (userLat != null && userLon != null) {
            try {
                val userBmp = MarkerIconHelper.getUserLocationBitmap(mode)
                mapView.layerManager.layers.add(
                    Marker(LatLong(userLat, userLon), userBmp, 0, -userBmp.height / 2)
                )
            } catch (_: Exception) {}
        }"""
new_user = """        if (userLat != null && userLon != null) {
            try {
                val userBmp = MarkerIconHelper.getUserLocationBitmap(MarkerIconHelper.DisplayMode.BUBBLE_MEDIUM)
                mapView.layerManager.layers.add(
                    Marker(LatLong(userLat, userLon), userBmp, 0, -userBmp.height / 2)
                )
            } catch (_: Exception) {}
        }"""
if old_user not in c:
    raise SystemExit("user marker block not found")
c = c.replace(old_user, new_user, 1)

old_add = """        val zoom = mapView.model.mapViewPosition.zoomLevel.toInt()
        val centerLat = mapView.model.mapViewPosition.center.latitude
        val mode = MarkerIconHelper.displayModeForZoom(zoom, centerLat)

        if (mode != MarkerIconHelper.DisplayMode.HIDDEN) {
            val ordered = if (highlightedStoreId == null) stores
            else stores.sortedBy { if (it.id == highlightedStoreId) 1 else 0 }
            ordered.forEach { store ->
                try {
                    val highlighted = highlightedStoreId != null && store.id == highlightedStoreId
                    var androidBmp = MarkerIconHelper.getAndroidMarkerBitmap(store.category, mode)
                        ?: return@forEach"""

new_add = """        val zoom = mapView.model.mapViewPosition.zoomLevel.toInt()
        val centerLat = mapView.model.mapViewPosition.center.latitude
        val mode = MarkerIconHelper.displayModeForZoom(zoom, centerLat)
        val zoomSize = MarkerIconHelper.sizeForZoom(zoom)

        if (mode != MarkerIconHelper.DisplayMode.HIDDEN && zoomSize > 0) {
            val ordered = if (highlightedStoreId == null) stores
            else stores.sortedBy { if (it.id == highlightedStoreId) 1 else 0 }
            ordered.forEach { store ->
                try {
                    val highlighted = highlightedStoreId != null && store.id == highlightedStoreId
                    var androidBmp = MarkerIconHelper.getAndroidMarkerBitmap(store.category, mode)
                        ?: return@forEach
                    if (androidBmp.width != zoomSize && zoomSize > 0) {
                        androidBmp = android.graphics.Bitmap.createScaledBitmap(
                            androidBmp, zoomSize, zoomSize, true
                        )
                    }"""

if old_add not in c:
    raise SystemExit("addMarkers block not found")
c = c.replace(old_add, new_add, 1)
p.write_text(c, encoding="utf-8")
print("MapScreen OK")

# ─── MarkerIconHelper.kt ───
p2 = Path("app/src/main/java/com/marketmaps/app/ui/map/MarkerIconHelper.kt")
c2 = p2.read_text(encoding="utf-8")

old_mode = """    fun displayModeForZoom(zoom: Int, latitude: Double = 30.0): DisplayMode {
        val approxScaleMeters = metersPerPixel(latitude, zoom) * 100.0
        return when {
            approxScaleMeters <= 70 -> DisplayMode.BUBBLE_LARGE
            approxScaleMeters <= 140 -> DisplayMode.BUBBLE_MEDIUM
            approxScaleMeters <= 280 -> DisplayMode.CIRCLE
            else -> DisplayMode.HIDDEN
        }
    }

    /**
     * أحجام أقرب لحجم أيقونات خرائط جوجل.
     * LARGE ≈ 48-52dp على الشاشات الشائعة.
     */
    fun sizeForMode(mode: DisplayMode): Int {
        return when (mode) {
            DisplayMode.BUBBLE_LARGE -> 96   // كان 67 — أكبر بكثير ليطابق جوجل
            DisplayMode.BUBBLE_MEDIUM -> 56  // كان 34
            DisplayMode.CIRCLE -> 20
            DisplayMode.HIDDEN -> 0
        }
    }"""

new_mode = """    fun displayModeForZoom(zoom: Int, latitude: Double = 30.0): DisplayMode {
        return when {
            zoom >= 16 -> DisplayMode.BUBBLE_LARGE
            zoom >= 14 -> DisplayMode.BUBBLE_MEDIUM
            zoom >= 12 -> DisplayMode.CIRCLE
            else -> DisplayMode.HIDDEN
        }
    }

    /** حجم الأيقونة حسب الزوم بخطوات متقاربة لانتقال أنعم. */
    fun sizeForZoom(zoom: Int): Int {
        return when {
            zoom >= 18 -> 88
            zoom >= 17 -> 80
            zoom >= 16 -> 72
            zoom >= 15 -> 64
            zoom >= 14 -> 56
            zoom >= 13 -> 44
            zoom >= 12 -> 32
            zoom >= 11 -> 22
            else -> 0
        }
    }

    fun sizeForMode(mode: DisplayMode): Int {
        return when (mode) {
            DisplayMode.BUBBLE_LARGE -> 80
            DisplayMode.BUBBLE_MEDIUM -> 56
            DisplayMode.CIRCLE -> 28
            DisplayMode.HIDDEN -> 0
        }
    }"""

if old_mode not in c2:
    raise SystemExit("MarkerIconHelper mode block not found")
c2 = c2.replace(old_mode, new_mode, 1)
p2.write_text(c2, encoding="utf-8")
print("MarkerIconHelper OK")

# ─── StoreDetailsDialog.kt ───
p3 = Path("app/src/main/java/com/marketmaps/app/ui/map/StoreDetailsDialog.kt")
c3 = p3.read_text(encoding="utf-8")
old_enter = """        enterY.animateTo(
            0f,
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        )"""
new_enter = """        enterY.animateTo(
            0f,
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)
        )"""
if old_enter not in c3:
    print("WARN: enterY not found")
else:
    c3 = c3.replace(old_enter, new_enter, 1)
    p3.write_text(c3, encoding="utf-8")
    print("StoreDetails OK")

print("ALL DONE")
