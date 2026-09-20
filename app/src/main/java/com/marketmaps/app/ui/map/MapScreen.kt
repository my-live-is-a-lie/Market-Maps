package com.marketmaps.app.ui.map

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OpenStreetMapMapnik
import org.mapsforge.map.rendertheme.InternalRenderTheme

/**
 * شاشة الخريطة الرئيسية.
 * حالياً تعرض خريطة OpenStreetMap عبر الإنترنت (الوضع الافتراضي).
 * لاحقاً سنضيف دعم الأوفلاين وتحميل الملفات.
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // تهيئة Mapsforge مرة واحدة
    remember {
        AndroidGraphicFactory.createInstance(context.applicationContext)
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // عرض الخريطة
            AndroidView(
                factory = { ctx ->
                    createMapView(ctx)
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView ->
                    // يمكن تحديث الخريطة هنا لاحقاً
                }
            )

            // نص توضيحي مؤقت في الأعلى
            Text(
                text = "Market Maps - الخريطة",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
        }
    }
}

/**
 * إنشاء MapView مع طبقة تحميل بلاطات OpenStreetMap عبر الإنترنت.
 */
private fun createMapView(context: Context): MapView {
    val mapView = MapView(context).apply {
        isClickable = true
        setBuiltInZoomControls(false) // سنضيف أزرارنا الخاصة لاحقاً
    }

    // إنشاء ذاكرة تخزين مؤقت للبلاطات
    val tileCache: TileCache = AndroidUtil.createTileCache(
        context,
        "mapcache",
        mapView.model.displayModel.tileSize,
        1.0f,
        mapView.model.frameBufferModel.overdrawFactor
    )

    // طبقة تحميل البلاطات من الإنترنت (OpenStreetMap)
    val tileSource = OpenStreetMapMapnik.INSTANCE
    tileSource.userAgent = "MarketMaps/1.0"

    val downloadLayer = TileDownloadLayer(
        tileCache,
        mapView.model.mapViewPosition,
        tileSource,
        AndroidGraphicFactory.INSTANCE
    )

    mapView.layerManager.layers.add(downloadLayer)

    // تفعيل الطبقة
    downloadLayer.onResume()

    // تعيين موقع افتراضي (القاهرة تقريباً)
    mapView.model.mapViewPosition.setCenter(LatLong(30.0444, 31.2357))
    mapView.model.mapViewPosition.zoomLevel = 12.toByte()

    // حفظ المرجع للتنظيف لاحقاً (سنحسنه في الخطوات القادمة)
    mapView.tag = downloadLayer

    return mapView
}
