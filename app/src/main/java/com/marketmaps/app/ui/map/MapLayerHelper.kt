package com.marketmaps.app.ui.map

import android.content.Context
import android.widget.Toast
import com.marketmaps.app.data.MapDownloader
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.layer.Layers
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OpenStreetMapMapnik
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.File

/**
 * إدارة طبقات الخريطة (أونلاين / أوفلاين).
 */
object MapLayerHelper {

    data class LayerBundle(
        val tileCache: TileCache,
        var downloadLayer: TileDownloadLayer? = null,
        var rendererLayer: TileRendererLayer? = null,
        var mapFile: MapFile? = null
    )

    fun createTileCache(context: Context, mapView: MapView): TileCache {
        // مضاعف أعلى يقلل ظهور مربعات رمادية أثناء التحريك/التكبير
        return AndroidUtil.createTileCache(
            context,
            "mapcache",
            mapView.model.displayModel.tileSize,
            2.5f,
            mapView.model.frameBufferModel.overdrawFactor
        )
    }

    fun clearBaseLayers(layers: Layers) {
        val toRemove = layers.filter {
            it is TileDownloadLayer || it is TileRendererLayer
        }
        toRemove.forEach { layers.remove(it) }
    }

    fun applyOnline(mapView: MapView, bundle: LayerBundle) {
        clearBaseLayers(mapView.layerManager.layers)
        bundle.rendererLayer?.onDestroy()
        bundle.rendererLayer = null
        bundle.mapFile?.close()
        bundle.mapFile = null

        val tileSource = OpenStreetMapMapnik.INSTANCE.apply { userAgent = "MarketMaps/1.0" }
        val downloadLayer = TileDownloadLayer(
            bundle.tileCache,
            mapView.model.mapViewPosition,
            tileSource,
            AndroidGraphicFactory.INSTANCE
        )
        mapView.layerManager.layers.add(0, downloadLayer)
        downloadLayer.onResume()
        bundle.downloadLayer = downloadLayer
        mapView.invalidate()
    }

    fun applyOffline(context: Context, mapView: MapView, bundle: LayerBundle): Boolean {
        val file = MapDownloader.egyptMapFile(context)
        if (!file.exists() || file.length() < 1_000_000) {
            Toast.makeText(context, "الخريطة غير محمّلة. افتح الإعدادات للتحميل.", Toast.LENGTH_LONG).show()
            return false
        }

        return try {
            clearBaseLayers(mapView.layerManager.layers)
            bundle.downloadLayer?.onPause()
            bundle.downloadLayer = null

            val mapFile = MapFile(file)
            val rendererLayer = AndroidUtil.createTileRendererLayer(
                bundle.tileCache,
                mapView.model.mapViewPosition,
                mapFile as MapDataStore,
                MapsforgeThemes.DEFAULT,
                false,
                true,
                false
            )
            mapView.layerManager.layers.add(0, rendererLayer)
            bundle.mapFile = mapFile
            bundle.rendererLayer = rendererLayer
            true
        } catch (e: Exception) {
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

    fun destroy(bundle: LayerBundle) {
        bundle.downloadLayer?.onPause()
        bundle.rendererLayer?.onDestroy()
        bundle.mapFile?.close()
        bundle.downloadLayer = null
        bundle.rendererLayer = null
        bundle.mapFile = null
    }
}
