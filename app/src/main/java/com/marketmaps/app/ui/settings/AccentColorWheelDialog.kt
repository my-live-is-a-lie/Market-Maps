package com.marketmaps.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** تحويل HSV إلى Color (يعتمد على android.graphics لتجنّب فروق إصدارات Compose) */
internal fun hsvToColor(hue: Float, saturation: Float, value: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))

/** استخراج HSV من Color */
internal fun colorToHsv(color: Color): Triple<Float, Float, Float> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return Triple(hsv[0], hsv[1], hsv[2])
}

/** صيغة #RRGGBB المستخدمة في مفتاح اللون المخصص */
internal fun colorToHex(color: Color): String =
    String.format("#%06X", 0xFFFFFF and color.toArgb())

/** ألوان حلقة العجلة (تدرّج كامل مع إعادة اللون الأول لإغلاق الحلقة بسلاسة) */
private fun hueRingColors(): List<Color> {
    val base = (0 until 24).map { i -> hsvToColor(i * 15f, 1f, 1f) }
    return base + base.first()
}

/**
 * هندسة العجلة: حلقة الهوى (Hue) في الخارج، ومربع التشبّع/السطوع في الداخل.
 * تُحسب بالنقاط (px) لأن حساب الرسم واللمس يحتاجان نفس الأرقام.
 */
private class WheelGeo(width: Float, height: Float) {
    val center = Offset(width / 2f, height / 2f)
    val minDim = minOf(width, height)
    val outerRadius = minDim / 2f
    val ringWidth = minDim * 0.15f
    val ringCenterRadius = outerRadius - ringWidth / 2f
    val innerRadius = outerRadius - ringWidth
    val squareSide = innerRadius * 1.3f
    val squareTopLeft = Offset(center.x - squareSide / 2f, center.y - squareSide / 2f)

    fun distance(point: Offset): Float = hypot(point.x - center.x, point.y - center.y)

    /** زاوية النقطة بالنسبة لمركز العجلة = درجة الهوى (0..360، نفس اتجاه تدرّج Sweep) */
    fun hueAt(point: Offset): Float {
        val deg = Math.toDegrees(
            atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble())
        ).toFloat()
        return (deg + 360f) % 360f
    }

    /** موقع النقطة داخل حلقة الهوى (خارج المربع الداخلي) */
    fun isOnRing(point: Offset): Boolean = distance(point) >= innerRadius

    /** التشبّع والسطوع من موقع داخل المربع (يُقيّدان داخل الحدود) */
    fun saturationAt(point: Offset): Float =
        ((point.x - squareTopLeft.x) / squareSide).coerceIn(0f, 1f)

    fun valueAt(point: Offset): Float =
        (1f - ((point.y - squareTopLeft.y) / squareSide)).coerceIn(0f, 1f)

    fun ringThumb(hue: Float): Offset {
        val rad = Math.toRadians(hue.toDouble())
        return Offset(
            (center.x + ringCenterRadius * cos(rad)).toFloat(),
            (center.y + ringCenterRadius * sin(rad)).toFloat()
        )
    }

    fun squareThumb(saturation: Float, value: Float): Offset = Offset(
        squareTopLeft.x + saturation * squareSide,
        squareTopLeft.y + (1f - value) * squareSide
    )
}

/**
 * عجلة اختيار الألوان: حلقة للهوى + مربع للتشبّع والسطوع، مع معاينة وكود اللون.
 * تُستخدم في قسم «مظهر التطبيق» لتحديد لون التمييز المخصص.
 */
@Composable
fun AccentColorWheelDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by remember { mutableFloatStateOf(initialHsv.first) }
    var saturation by remember { mutableFloatStateOf(initialHsv.second) }
    var value by remember { mutableFloatStateOf(initialHsv.third) }

    val picked = hsvToColor(hue, saturation, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عجلة اختيار الألوان") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ColorWheel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onHueChange = { hue = it },
                    onSaturationValueChange = { s, v -> saturation = s; value = v },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(picked, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Column {
                        Text(
                            text = "اللون المختار",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = colorToHex(picked),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(picked) }) { Text("تطبيق") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun ColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueChange: (Float) -> Unit,
    onSaturationValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 1 = السحب على حلقة الهوى، 2 = السحب داخل مربع التشبّع/السطوع
    var dragTarget by remember { mutableIntStateOf(0) }
    val ringColors = remember { hueRingColors() }

    // الهندسة نفسها للرسم واللمس (تعتمد على مقاس اللوحة الفعلي)
    val geo = remember(canvasSize) {
        if (canvasSize.width == 0) null
        else WheelGeo(canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }

    fun applyTouch(point: Offset, target: Int) {
        val g = geo ?: return
        when (target) {
            1 -> onHueChange(g.hueAt(point))
            2 -> onSaturationValueChange(g.saturationAt(point), g.valueAt(point))
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { point ->
                        val g = geo
                        dragTarget = when {
                            g == null -> 0
                            g.isOnRing(point) -> 1
                            else -> 2
                        }
                        applyTouch(point, dragTarget)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        applyTouch(change.position, dragTarget)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    val g = geo ?: return@detectTapGestures
                    val target = if (g.isOnRing(point)) 1 else 2
                    applyTouch(point, target)
                }
            }
    ) {
        val g = geo ?: WheelGeo(size.width, size.height)
        val pureHue = hsvToColor(hue, 1f, 1f)

        // حلقة الهوى
        drawCircle(
            brush = Brush.sweepGradient(ringColors),
            radius = g.ringCenterRadius,
            center = g.center,
            style = Stroke(width = g.ringWidth)
        )
        // إطار خفيف حول الحلقة للفصل عن الخلفية
        drawCircle(
            color = Color.Gray.copy(alpha = 0.35f),
            radius = g.outerRadius,
            center = g.center,
            style = Stroke(width = 1f)
        )
        drawCircle(
            color = Color.Gray.copy(alpha = 0.35f),
            radius = g.innerRadius,
            center = g.center,
            style = Stroke(width = 1f)
        )

        // مربع التشبّع (أفقياً) والسطوع (عمودياً)
        val squareSize = Size(g.squareSide, g.squareSide)
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.White, pureHue)),
            topLeft = g.squareTopLeft,
            size = squareSize
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            topLeft = g.squareTopLeft,
            size = squareSize
        )
        drawRect(
            color = Color.Gray.copy(alpha = 0.35f),
            topLeft = g.squareTopLeft,
            size = squareSize,
            style = Stroke(width = 1f)
        )

        // مقبض حلقة الهوى
        val ringThumb = g.ringThumb(hue)
        drawCircle(color = Color.White, radius = g.ringWidth * 0.42f, center = ringThumb)
        drawCircle(color = pureHue, radius = g.ringWidth * 0.30f, center = ringThumb)
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            radius = g.ringWidth * 0.42f,
            center = ringThumb,
            style = Stroke(width = 1.5f)
        )

        // مقبض التشبّع/السطوع
        val squareThumb = g.squareThumb(saturation, value)
        drawCircle(
            color = hsvToColor(hue, saturation, value),
            radius = g.innerRadius * 0.09f,
            center = squareThumb
        )
        drawCircle(
            color = Color.White,
            radius = g.innerRadius * 0.09f,
            center = squareThumb,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            radius = g.innerRadius * 0.09f + 2f,
            center = squareThumb,
            style = Stroke(width = 1f)
        )
    }
}

/** ألوان حلقة العجلة للاستخدام كأيقونة في قائمة الألوان */
@Composable
internal fun rememberHueRingBrush(): Brush = remember { Brush.sweepGradient(hueRingColors()) }
