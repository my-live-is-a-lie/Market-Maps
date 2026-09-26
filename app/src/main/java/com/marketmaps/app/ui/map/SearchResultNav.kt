package com.marketmaps.app.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * بطاقة تنقل نتائج البحث — مضغوطة على اليسار.
 * السحب لليسار يغلقها مع حركة نابضية.
 */
@Composable
fun SearchResultNav(
    currentIndex: Int,
    total: Int,
    storeName: String,
    distanceText: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (total <= 1 || currentIndex < 0) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }
    val dismissPx = with(density) { 72.dp.toPx() }
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.45f
    val bg = if (isDark) Color(0xE6121212) else scheme.surface.copy(alpha = 0.95f)
    val titleColor = scheme.onSurface
    val distanceColor = if (isDark) Color.White else Color(0xFF202124)
    val muted = if (isDark) Color.White.copy(alpha = 0.55f) else scheme.onSurface.copy(alpha = 0.45f)

    Column(
        modifier = modifier
            .widthIn(min = 148.dp, max = 168.dp)
            .absoluteOffset { IntOffset(offsetX.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        // absoluteOffset: السالب = يسار الشاشة دائماً (لا ينعكس مع العربية)
                        // نسمح بالتحريك لليسار فقط
                        val next = (offsetX.value + drag.x).coerceIn(-dismissPx * 2.5f, 0f)
                        scope.launch { offsetX.snapTo(next) }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value <= -dismissPx * 0.40f) {
                                offsetX.animateTo(
                                    -dismissPx * 3f,
                                    spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
                                )
                                onDismiss()
                            } else {
                                offsetX.animateTo(
                                    0f,
                                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                        }
                    }
                )
            }
            .background(bg, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = storeName,
                color = titleColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = titleColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (!distanceText.isNullOrBlank()) {
            Text(
                text = distanceText,
                color = distanceColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = currentIndex > 0,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "السابق",
                    tint = if (currentIndex > 0) scheme.primary else muted,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = "${currentIndex + 1}/$total",
                color = titleColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            IconButton(
                onClick = onNext,
                enabled = currentIndex < total - 1,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "التالي",
                    tint = if (currentIndex < total - 1) scheme.primary else muted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
