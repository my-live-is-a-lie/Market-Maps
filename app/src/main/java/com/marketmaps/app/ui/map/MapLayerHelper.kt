package com.marketmaps.app.ui.map

import android.content.Context
import android.widget.Toast
import com.marketmaps.app.data.MapDownloader
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.layer.Layers
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.cache.TwoLevelTileCache
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OpenStreetMapMapnik
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.File
import com.marketmaps.app.util.logE
import com.marketmaps.app.util.logW

/**
 * إدارة طبقات الخريطة (أونلاين / أوفلاين).
 */
object MapLayerHelper {

    /**
     * كاش منفصل لكل مصدر:
     * - onlineCache: بلاطات OSM الجاهزة — دائم (persistent) على القرص فيُعاد استخدامه بين
     *   الجلسات ويقلل الطلبات على خوادم OSM.
     * - offlineCache: بلاطات مرسومة من ملف .map — غير دائم، لأن Mapsforge يحذّر من أن الكاش
     *   الدائم مع TileRendererLayer قد يقصّ التسميات عند حواف البلاطات، ولأن مفتاح الكاش
     *   (z/x/y) لا يميّز بين المصدرين فخلطهما في كاش واحد يعرض بلاطات المصدر الآخر.
     */
    data class LayerBundle(
        val onlineCache: TileCache,
        val offlineCache: TileCache,
        var downloadLayer: TileDownloadLayer? = null,
        var rendererLayer: TileRendererLayer? = null,
        var mapFile: MapFile? = null
    )

    /**
     * نسبة الشاشة للكاش في الذاكرة. كانت 2.5 (أي كاش بحجم 2.5 شاشة من البلاطات بجانب
     * الـ frame buffer) وهذا مع تضخيم حجم البلاطة كان يستهلك >130MB على هواتف كثيرة.
     * 1.5 كافية لتغطية التحريك، والقرص يغطي الباقي.
     */
    private const val SCREEN_RATIO = 1.5f

    /** تكبير خفيف لنصوص الأوفلاين فقط بدل تضخيم الرسم كله عبر userScaleFactor */
    private const val OFFLINE_TEXT_SCALE = 1.15f

    fun createBundle(context: Context, mapView: MapView): LayerBundle {
        deleteLegacyCache(context)
        return LayerBundle(
            onlineCache = createTileCache(context, mapView, "mapcache_online", persistent = true),
            offlineCache = createTileCache(context, mapView, "mapcache_offline", persistent = false)
        )
    }

    private fun createTileCache(
        context: Context,
        mapView: MapView,
        id: String,
        persistent: Boolean
    ): TileCache = AndroidUtil.createTileCache(
        context,
        id,
        mapView.model.displayModel.tileSize,
        SCREEN_RATIO,
        mapView.model.frameBufferModel.overdrawFactor,
        persistent
    )

    /** الكاش القديم المشترك "mapcache" لم يعد مستخدماً — نحذفه مرة واحدة لتحرير القرص */
    private fun deleteLegacyCache(context: Context) {
        try {
            val legacy = File(context.externalCacheDir ?: return, "mapcache")
            if (legacy.exists()) legacy.deleteRecursively()
        } catch (e: Exception) {
            logW(TAG, "تعذر حذف الكاش القديم", e)
        }
    }

    fun clearBaseLayers(layers: Layers) {
        val toRemove = layers.filter {
            it is TileDownloadLayer || it is TileRendererLayer
        }
        if (toRemove.isNotEmpty()) layers.removeAll(toRemove, false)
    }

    /** إيقاف وتدمير طبقة التحميل (خيوطها) — onPause وحده يبقي الخيوط حية */
    private fun destroyDownloadLayer(bundle: LayerBundle) {
        try {
            bundle.downloadLayer?.onPause()
            bundle.downloadLayer?.onDestroy()
        } catch (e: Exception) {
            logW(TAG, "تدمير طبقة التحميل", e)
        }
        bundle.downloadLayer = null
    }

    /** TileRendererLayer.onDestroy يغلق ملف الخريطة (MapDataStore) بنفسه */
    private fun destroyRendererLayer(bundle: LayerBundle) {
        try {
            bundle.rendererLayer?.onDestroy()
        } catch (e: Exception) {
            logW(TAG, "تدمير طبقة الأوفلاين", e)
        }
        bundle.rendererLayer = null
        bundle.mapFile = null
    }

    fun applyOnline(mapView: MapView, bundle: LayerBundle) {
        // إيقاف وتدمير الطبقة القديمة قبل استبدالها لتفادي تسريب خيوط التحميل
        destroyDownloadLayer(bundle)
        clearBaseLayers(mapView.layerManager.layers)
        destroyRendererLayer(bundle)

        val tileSource = OpenStreetMapMapnik.INSTANCE.apply {
            // يُفضّل لاحقاً إضافة وسيلة تواصل وفق سياسة OSM
            userAgent = "MarketMaps/1.0 (Android; https://github.com/my-live-is-a-lie/Market-Maps)"
        }
        val downloadLayer = TileDownloadLayer(
            bundle.onlineCache,
            mapView.model.mapViewPosition,
            tileSource,
            AndroidGraphicFactory.INSTANCE
        )
        mapView.layerManager.layers.add(0, downloadLayer)
        downloadLayer.onResume()
        bundle.downloadLayer = downloadLayer
        mapView.invalidate()
    }

    fun applyOffline(
        context: Context,
        mapView: MapView,
        bundle: LayerBundle,
        fileName: String? = null
    ): Boolean {
        val name = fileName?.takeIf { it.isNotBlank() }
            ?: MapDownloader.listDownloaded(context).firstOrNull()?.fileName
            ?: "egypt.map"
        val file = MapDownloader.mapFile(context, name)
        if (!file.exists() || file.length() < 1_000_000) {
            Toast.makeText(context, "الخريطة غير محمّلة. افتح الإعدادات للتحميل.", Toast.LENGTH_LONG).show()
            return false
        }

        return try {
            clearBaseLayers(mapView.layerManager.layers)
            // كانت الطبقة القديمة تُوقف فقط (onPause) فتبقى خيوطها حية — نفس تسريب applyOnline
            destroyDownloadLayer(bundle)
            // عند التبديل بين ملفي خرائط: أغلق الملف القديم وامسح بلاطاته من الكاش
            if (bundle.rendererLayer != null) {
                destroyRendererLayer(bundle)
                bundle.offlineCache.purge()
            }

            val mapFile = MapFile(file)
            val rendererLayer = AndroidUtil.createTileRendererLayer(
                bundle.offlineCache,
                mapView.model.mapViewPosition,
                mapFile as MapDataStore,
                MapsforgeThemes.DEFAULT,
                false,
                true,
                false
            )
            rendererLayer.setTextScale(OFFLINE_TEXT_SCALE)
            mapView.layerManager.layers.add(0, rendererLayer)
            bundle.mapFile = mapFile
            bundle.rendererLayer = rendererLayer
            true
        } catch (e: Exception) {
            logE(TAG, "تعذر فتح الخريطة الأوفلاين $fileName", e)
            Toast.makeText(context, "تعذر فتح الخريطة الأوفلاين: ${e.message}", Toast.LENGTH_LONG).show()
            applyOnline(mapView, bundle)
            false
        }
    }

    fun pause(bundle: LayerBundle) {
        bundle.downloadLayer?.onPause()
    }

    fun resume(bundle: LayerBundle) {
        bundle.downloadLayer?.onResume()
    }

    /**
     * تفريغ بلاطات الذاكرة فقط (عشرات الميجابايت) عند ضغط الذاكرة أو الخروج للخلفية.
     * لا نستدعي purge() على TwoLevelTileCache مباشرة لأنه يحذف أيضاً بلاطات القرص
     * المحفوظة للأونلاين. يُستدعى فقط والخريطة غير ظاهرة.
     */
    fun trimMemory(bundle: LayerBundle) {
        for (cache in listOf(bundle.onlineCache, bundle.offlineCache)) {
            val memoryLevel = when (cache) {
                is TwoLevelTileCache -> cache.firstLevelTileCache
                is InMemoryTileCache -> cache
                else -> null
            }
            memoryLevel?.purge()
        }
    }

    /**
     * تحرير كل موارد الخريطة عند إزالتها من الشاشة.
     * سابقاً: mapView.destroy() لا يدمّر الطبقات ولا الكاش، فتبقى خيوط التحميل
     * وبلاطات الذاكرة (عشرات الميجابايت) حية بعد كل خروج من شاشة الخريطة.
     *
     * لا نستخدم mapView.destroyAll() لأنه يستدعي onDestroy على الـ Markers أيضاً،
     * وهذا يعيد صورها إلى مجمّع إعادة الاستخدام في Mapsforge بينما هي نفسها محفوظة في
     * كاش الأيقونات (MarkerIconHelper) — فتُرسم فوقها بلاطات وتظهر الأيقونات مشوهة.
     */
    fun destroy(mapView: MapView, bundle: LayerBundle) {
        val layers = mapView.layerManager.layers
        // إزالة كل الطبقات بدون onDestroy للـ Markers
        val all = layers.toList()
        if (all.isNotEmpty()) layers.removeAll(all, false)
        destroyDownloadLayer(bundle)
        destroyRendererLayer(bundle)
        try {
            bundle.onlineCache.destroy()
            bundle.offlineCache.destroy()
        } catch (e: Exception) {
            logW(TAG, "تدمير كاش البلاطات", e)
        }
        mapView.destroy()
    }
}

private const val TAG = "MapLayers"
