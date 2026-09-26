package com.marketmaps.app.ui.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.caverock.androidsvg.SVG
import com.marketmaps.app.data.Store
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun StoreDetailsBottomCard(
    store: Store,
    distanceMeters: Double?,
    onDismiss: () -> Unit,
    onEdit: (Store) -> Unit,
    onHeightChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenW = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    var showCoords by remember { mutableStateOf(false) }
    var dragAxis by remember { mutableStateOf<Char?>(null) } // 'H' أو 'V'
    var cardW by remember { mutableStateOf(1f) }
    var cardH by remember { mutableStateOf(1f) }

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val enterY = remember { Animatable(72f) }

    LaunchedEffect(store.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
        enterY.snapTo(72f)
        enterY.animateTo(
            0f,
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)
        )
    }

    val bounce = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val pencilIcon = remember { loadAssetSvgBitmap(context, "icons/pencil.svg", 40) }
    val pinIcon = remember { loadAssetSvgBitmap(context, "icons/pin.svg", 40) }
    val placeIcon = remember(store.category) {
        loadCategoryGlyph(context, store.category, 48)
    }

    val title = if (store.category.isNotBlank()) {
        "${store.category}: ${store.name}"
    } else store.name

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .onGloballyPositioned { coords ->
                    cardW = coords.size.width.toFloat().coerceAtLeast(1f)
                    cardH = coords.size.height.toFloat().coerceAtLeast(1f)
                    onHeightChanged(coords.size.height)
                }
                .graphicsLayer {
                    translationY = enterY.value
                }
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .pointerInput(store.id) {
                    detectDragGestures(
                        onDragStart = {
                            showCoords = false
                            dragAxis = null
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            if (dragAxis == null) {
                                val ax = abs(drag.x)
                                val ay = abs(drag.y)
                                dragAxis = when {
                                    ax > ay + 6f -> 'H'
                                    drag.y > 6f -> 'V'
                                    else -> null
                                }
                            }
                            when (dragAxis) {
                                'H' -> scope.launch { offsetX.snapTo(offsetX.value + drag.x * 1.15f) }
                                'V' -> scope.launch {
                                    offsetY.snapTo((offsetY.value + drag.y).coerceAtLeast(0f))
                                }
                                else -> Unit
                            }
                        },
                        onDragEnd = {
                            val threshX = cardW * 0.15f // سحب جانبي أسهل
                            val threshY = cardH * 0.28f
                            val goH = abs(offsetX.value) >= threshX
                            val goV = offsetY.value >= threshY
                            scope.launch {
                                if (goH || goV) {
                                    // إنزال الزائد فوراً قبل انتهاء حركة الإغلاق
                                    onHeightChanged(0)
                                    onDismiss()
                                } else {
                                    launch { offsetX.animateTo(0f, bounce) }
                                    launch { offsetY.animateTo(0f, bounce) }
                                }
                                dragAxis = null
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                launch { offsetX.animateTo(0f, bounce) }
                                launch { offsetY.animateTo(0f, bounce) }
                                dragAxis = null
                            }
                        }
                    )
                }
        ) {
            val scheme = MaterialTheme.colorScheme
            val isDark = scheme.surface.luminance() < 0.45f
            val bodyColor = if (isDark) Color.White else scheme.onSurfaceVariant
            val titleColor = scheme.onSurface
            val iconTint = if (isDark) Color.White else Color(0xFF202124)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = scheme.surface,
                contentColor = scheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(32.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(scheme.outlineVariant)
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(44.dp)
                        ) {
                            if (placeIcon != null) {
                                Image(
                                    bitmap = placeIcon.asImageBitmap(),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(iconTint),
                                    modifier = Modifier.size(26.dp)
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .background(scheme.onSurface, RoundedCornerShape(5.dp))
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formatDistanceAr(distanceMeters),
                                color = bodyColor,
                                fontSize = 11.sp,
                                lineHeight = 13.sp
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = titleColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Start,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (store.description.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = store.description,
                                        color = bodyColor,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        textAlign = TextAlign.Start,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionCircleButton(
                            bitmap = pencilIcon,
                            contentDescription = "تعديل",
                            onClick = {
                                showCoords = false
                                onEdit(store)
                            }
                        )

                        // حجم ثابت حتى لا يقفز الزر عند ظهور النافذة
                        Box(
                            modifier = Modifier.size(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ActionCircleButton(
                                bitmap = pinIcon,
                                contentDescription = "إحداثيات",
                                onClick = { showCoords = !showCoords },
                                onLongClick = {
                                    copyToClipboard(
                                        context,
                                        "عرض: ${store.latitude}\nطول: ${store.longitude}"
                                    )
                                    Toast.makeText(context, "تم نسخ الإحداثيات", Toast.LENGTH_SHORT).show()
                                    showCoords = false
                                }
                            )
                            if (showCoords) {
                                val density = LocalDensity.current
                                val gapPx = with(density) { 8.dp.roundToPx() }
                                val positionProvider = remember(gapPx) {
                                    object : PopupPositionProvider {
                                        override fun calculatePosition(
                                            anchorBounds: IntRect,
                                            windowSize: IntSize,
                                            layoutDirection: LayoutDirection,
                                            popupContentSize: IntSize
                                        ): IntOffset {
                                            // توسيط أفقي فوق زر الدبوس
                                            val x = anchorBounds.left +
                                                (anchorBounds.width - popupContentSize.width) / 2
                                            val y = anchorBounds.top - popupContentSize.height - gapPx
                                            val xClamped = x.coerceIn(
                                                0,
                                                (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                                            )
                                            val yClamped = y.coerceAtLeast(0)
                                            return IntOffset(xClamped, yClamped)
                                        }
                                    }
                                }
                                Popup(
                                    popupPositionProvider = positionProvider,
                                    onDismissRequest = { showCoords = false },
                                    properties = PopupProperties(
                                        focusable = true,
                                        dismissOnClickOutside = true,
                                        clippingEnabled = false
                                    )
                                ) {
                                    CoordsPopup(
                                        latitude = store.latitude,
                                        longitude = store.longitude
                                    )
                                }
                            }
                        }
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
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .size(28.dp)
            .shadow(1.5.dp, CircleShape)
            .clip(CircleShape)
            .background(accent)
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onLongPress = { onLongClick?.invoke() },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            // الأيقونات بيضاء في الـ SVG؛ نعرضها كما هي فوق لون التمييز
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.size(14.dp)
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
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .wrapContentWidth()
            .shadow(6.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = scheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "احداثيات الموقع",
                color = scheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "عرض ${"%.5f".format(latitude)}",
                color = scheme.onPrimaryContainer,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "طول ${"%.5f".format(longitude)}",
                color = scheme.onPrimaryContainer,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun formatDistanceAr(meters: Double?): String {
    if (meters == null || meters.isNaN() || meters < 0) return "—"
    return if (meters < 1000) "${meters.roundToInt()} متر"
    else String.format("%.1f كم", meters / 1000.0)
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

private fun loadCategoryGlyph(context: Context, category: String, sizePx: Int): Bitmap? {
    return try {
        val name = MarkerIconHelper.iconNameForCategory(category)
            ?: guessIconName(category)
        val path = "markers/$name.svg"
        loadAssetSvgBitmap(context, path, sizePx)
            ?: loadAssetSvgBitmap(context, "markers/store.svg", sizePx)
    } catch (_: Exception) {
        loadAssetSvgBitmap(context, "markers/store.svg", sizePx)
    }
}

private fun guessIconName(category: String): String {
    val c = category.lowercase()
    return when {
        "بقال" in c || "محل" in c -> "store"
        "مطعم" in c || "مقهى" in c || "كاف" in c -> "restaurant"
        "صيدل" in c || "صح" in c -> "hospital"
        "ورشة" in c -> "workshop"
        "مدرس" in c -> "school"
        "مسجد" in c -> "other"
        else -> "store"
    }
}
