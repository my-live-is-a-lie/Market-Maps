package com.marketmaps.app.ui.map

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OpenStreetMapMapnik

/**
 * شاشة الخريطة الرئيسية.
 * تحتوي على زر سفلي أيسر ينبثق منه زرّان:
 * 1. تحديد موقعي الحالي
 * 2. إضافة محل (يفعل وضع الإضافة)
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

    // حالة فتح قائمة الأزرار
    var isMenuExpanded by remember { mutableStateOf(false) }

    // حالة وضع إضافة محل
    var isAddMode by remember { mutableStateOf(false) }

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
                modifier = Modifier.fillMaxSize()
            )

            // نص وضع الإضافة (يظهر في الأسفل عندما يكون الوضع مفعلاً)
            AnimatedVisibility(
                visible = isAddMode,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            ) {
                Text(
                    text = "اضغط مطولاً على أي مكان في الخريطة لإضافة أو تعريف موقع جديد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                )
            }

            // مجموعة الأزرار في أسفل الشاشة أقصى اليسار
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // الزرّان المنبثقان (يظهران عند فتح القائمة)
                AnimatedVisibility(
                    visible = isMenuExpanded,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // زر تحديد موقعي الحالي
                        FloatingActionButton(
                            onClick = {
                                // TODO: سنضيف منطق تحديد الموقع لاحقاً
                                isMenuExpanded = false
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "موقعي الحالي"
                            )
                        }

                        // زر إضافة محل
                        FloatingActionButton(
                            onClick = {
                                isAddMode = !isAddMode
                                isMenuExpanded = false
                            },
                            containerColor = if (isAddMode)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = if (isAddMode)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onTertiaryContainer,
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

                // الزر الرئيسي الذي يفتح/يغلق القائمة
                FloatingActionButton(
                    onClick = {
                        isMenuExpanded = !isMenuExpanded
                    },
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
        }
    }
}

/**
 * إنشاء MapView مع طبقة تحميل بلاطات OpenStreetMap عبر الإنترنت.
 */
private fun createMapView(context: Context): MapView {
    val mapView = MapView(context).apply {
        isClickable = true
        setBuiltInZoomControls(false)
    }

    val tileCache: TileCache = AndroidUtil.createTileCache(
        context,
        "mapcache",
        mapView.model.displayModel.tileSize,
        1.0f,
        mapView.model.frameBufferModel.overdrawFactor
    )

    val tileSource = OpenStreetMapMapnik.INSTANCE
    tileSource.userAgent = "MarketMaps/1.0"

    val downloadLayer = TileDownloadLayer(
        tileCache,
        mapView.model.mapViewPosition,
        tileSource,
        AndroidGraphicFactory.INSTANCE
    )

    mapView.layerManager.layers.add(downloadLayer)
    downloadLayer.onResume()

    // موقع افتراضي: القاهرة
    mapView.model.mapViewPosition.setCenter(LatLong(30.0444, 31.2357))
    mapView.model.mapViewPosition.zoomLevel = 12.toByte()

    mapView.tag = downloadLayer

    return mapView
}
