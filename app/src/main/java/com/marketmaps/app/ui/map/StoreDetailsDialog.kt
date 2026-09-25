package com.marketmaps.app.ui.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caverock.androidsvg.SVG
import com.marketmaps.app.data.Store
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * بطاقة تفاصيل الموقع أسفل الشاشة (بدل النافذة المنبثقة القديمة).
 */
@Composable
fun StoreDetailsBottomCard(
    store: Store,
    distanceMeters: Double?,
    onDismiss: () -> Unit,
    onEdit: (Store) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var showCoords by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { appeared = true }

    val enterScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardScale"
    )
    val enterAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cardAlpha"
    )

    val dismissThreshold = with(density) { 100.dp.toPx() }
    val categoryIcon = remember(store.category) {
        loadCategoryIconBitmap(context, store.category, 96)
    }
    val pencilIcon = remember { loadAssetSvgBitmap(context, "icons/pencil.svg", 48) }
    val pinIcon = remember { loadAssetSvgBitmap(context, "icons/pin.svg", 48) }

    val title = if (store.category.isNotBlank()) {
        "${store.category}: ${store.name}"
    } else {
        store.name
    }
    val distanceText = formatDistanceAr(distanceMeters)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = enterScale
                scaleY = enterScale
                alpha = enterAlpha
            }
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (abs(offsetX) > dismissThreshold || offsetY > dismissThreshold) {
                            onDismiss()
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        offsetX += drag.x
                        offsetY += drag.y.coerceAtLeast(-40f) // سمح بسحب خفيف للأعلى
                    }
                )
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // مقبض السحب
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFDADCE0))
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // أيقونة المكان + المسافة (يسار بصرياً في RTL = نهاية الصف)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        if (categoryIcon != null) {
                            Image(
                                bitmap = categoryIcon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF1B1B1B), RoundedCornerShape(8.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = distanceText,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF5F6368),
                            fontSize = 12.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF202124),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (store.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = store.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF5F6368),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // أزرار القلم والدبوس
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        ActionCircleButton(
                            bitmap = pencilIcon,
                            contentDescription = "تعديل",
                            onClick = { showCoords = false; onEdit(store) }
                        )
                    }

                    Box {
                        if (showCoords) {
                            CoordsPopup(
                                latitude = store.latitude,
                                longitude = store.longitude,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(y = (-52).dp)
                            )
                        }
                        ActionCircleButton(
                            bitmap = pinIcon,
                            contentDescription = "إحداثيات",
                            onClick = { showCoords = !showCoords },
                            onLongClick = {
                                val text = "عرض: ${store.latitude}\nطول: ${store.longitude}"
                                copyToClipboard(context, text)
                                Toast.makeText(context, "تم نسخ الإحداثيات", Toast.LENGTH_SHORT).show()
                                showCoords = false
                            }
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun ActionCircleButton(
    bitmap: Bitmap?,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val green = Color(0xFF1B7A3D)
    Box(
        modifier = Modifier
            .size(36.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(green)
            .then(
                if (onLongClick != null) {
                    Modifier.pointerInput(Unit) {
                        // long press + click
                        detectTapGestures(
                            onLongPress = { onLongClick() },
                            onTap = { onClick() }
                        )
                    }
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CoordsPopup(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 200.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFE8F0FE)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = "احداثيات الموقع",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF1A73E8),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "عرض ${"%.6f".format(latitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF3C4043)
            )
            Text(
                text = "طول ${"%.6f".format(longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF3C4043)
            )
        }
    }
}

private fun formatDistanceAr(meters: Double?): String {
    if (meters == null || meters.isNaN()) return "—"
    return when {
        meters < 1000 -> "${meters.roundToInt()} متر"
        else -> String.format("%.1f كم", meters / 1000.0)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("coords", text))
}

private fun loadAssetSvgBitmap(context: Context, assetPath: String, sizePx: Int): Bitmap? {
    return try {
        val svg = SVG.getFromAsset(context.assets, assetPath)
        svg.setDocumentWidth(sizePx.toFloat())
        svg.setDocumentHeight(sizePx.toFloat())
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        svg.renderToCanvas(canvas)
        bmp
    } catch (_: Exception) {
        null
    }
}

private fun loadCategoryIconBitmap(context: Context, category: String, sizePx: Int): Bitmap? {
    return try {
        MarkerIconHelper.init(context)
        MarkerIconHelper.getAndroidMarkerBitmap(
            category,
            MarkerIconHelper.DisplayMode.BUBBLE_MEDIUM
        )?.let { src ->
            Bitmap.createScaledBitmap(src, sizePx, sizePx, true)
        }
    } catch (_: Exception) {
        null
    }
}

// إبقاء الاسم القديم للتوافق إن وُجدت استدعاءات قديمة — يوجّه للبطاقة السفلية عبر MapScreen
@Deprecated("استخدم StoreDetailsBottomCard")
@Composable
fun StoreDetailsDialog(
    store: Store,
    onDismiss: () -> Unit,
    onEdit: (Store) -> Unit,
    onDelete: (Store) -> Unit
) {
    // لم يعد يُستخدم كـ Dialog؛ الحذف من التعديل لاحقاً
    onDismiss()
}
