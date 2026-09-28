package com.marketmaps.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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

/** نسختا العجلة المتاحتان للمستخدم */
enum class ColorPickerStyle(val key: String, val label: String) {
    DISC("disc", "قرص ملوّن"),
    RING_SQUARE("ring_square", "حلقة + مربع");

    companion object {
        fun fromKey(key: String?): ColorPickerStyle =
            entries.firstOrNull { it.key == key } ?: DISC
    }
}

/** ألوان القرص/الحلقة (تدرّج كامل مع إعادة اللون الأول لإغلاق الدائرة بسلاسة) */
private fun hueRingColors(): List<Color> {
    val base = (0 until 24).map { i -> hsvToColor(i * 15f, 1f, 1f) }
    return base + base.first()
}

/**
 * هندسة القرص: الزاوية = درجة اللون (Hue)، والمسافة من المركز = التشبّع (Saturation).
 * تُحسب بالنقاط (px) لأن الرسم واللمس يحتاجان نفس الأرقام.
 */
private class DiscGeo(width: Float, height: Float) {
    val center = Offset(width / 2f, height / 2f)
    // هامش صغير حتى لا يُقصّ المقبض عند حدود القرص
    val radius = minOf(width, height) / 2f * 0.94f

    fun saturationAt(point: Offset): Float =
        (hypot(point.x - center.x, point.y - center.y) / radius).coerceIn(0f, 1f)

    /** زاوية النقطة بالنسبة للمركز = درجة اللون (نفس اتجاه تدرّج Sweep) */
    fun hueAt(point: Offset): Float {
        val deg = Math.toDegrees(
            atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble())
        ).toFloat()
        return (deg + 360f) % 360f
    }

    fun thumb(hue: Float, saturation: Float): Offset {
        val rad = Math.toRadians(hue.toDouble())
        val dist = saturation * radius
        return Offset(
            center.x + (dist * cos(rad)).toFloat(),
            center.y + (dist * sin(rad)).toFloat()
        )
    }
}

/**
 * عجلة اختيار الألوان بنسختين يبدّل بينهما المستخدم:
 * 1) قرص ملوّن بالكامل + شريط سطوع.
 * 2) حلقة للهوى + مربع للتشبّع والسطوع.
 * مع معاينة اللون وكوده وزر نسخ، والحفظ في تفضيلات المستخدم.
 */
@Composable
fun AccentColorWheelDialog(
    initialColor: Color,
    style: ColorPickerStyle,
    onStyleChange: (ColorPickerStyle) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val initialHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by remember { mutableFloatStateOf(initialHsv.first) }
    var saturation by remember { mutableFloatStateOf(initialHsv.second) }
    var value by remember { mutableFloatStateOf(initialHsv.third) }

    val picked = hsvToColor(hue, saturation, value)
    val hex = colorToHex(picked)
    // في النمطين الغامق والمظلم تكون أزرار النافذة (نسخ/إلغاء) باللون الأبيض
    val darkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val actionColor = if (darkSurface) Color.White else MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عجلة اختيار الألوان") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // زر التبديل بين النسختين
                PickerStyleToggle(style = style, onStyleChange = onStyleChange)

                when (style) {
                    ColorPickerStyle.DISC -> {
                        ColorDisc(
                            hue = hue,
                            saturation = saturation,
                            onPick = { h, s -> hue = h; saturation = s },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        )
                        BrightnessBar(
                            hue = hue,
                            value = value,
                            onValueChange = { value = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )
                    }
                    ColorPickerStyle.RING_SQUARE -> {
                        ColorRingSquare(
                            hue = hue,
                            saturation = saturation,
                            value = value,
                            onHueChange = { hue = it },
                            onSaturationValueChange = { s, v -> saturation = s; value = v },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(picked, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "اللون المختار",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = hex,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    // نسخ رمز اللون إلى الحافظة (الزر في الأسفل بمحاذاة سطر الرمز)
                    TextButton(
                        modifier = Modifier.align(Alignment.Bottom),
                        colors = ButtonDefaults.textButtonColors(contentColor = actionColor),
                        onClick = {
                            clipboard.setText(AnnotatedString(hex))
                            Toast.makeText(context, "تم نسخ رمز اللون $hex", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("نسخ")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(picked) }) { Text("تطبيق") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = actionColor)
            ) {
                Text("إلغاء")
            }
        }
    )
}

/** مبدّل النسختين: خياران ظاهران (قرص ملوّن / حلقة + مربع) */
@Composable
private fun PickerStyleToggle(
    style: ColorPickerStyle,
    onStyleChange: (ColorPickerStyle) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, scheme.outline.copy(alpha = 0.5f), shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(ColorPickerStyle.DISC, ColorPickerStyle.RING_SQUARE).forEach { option ->
            val selected = option == style
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) scheme.primaryContainer else Color.Transparent)
                    .clickable { onStyleChange(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                )
            }
        }
    }
}

/** القرص الملوّن: يُسحب من أي نقطة فيه (من مركزه حتى حافته) لتحديد اللون والتشبّع */
@Composable
private fun ColorDisc(
    hue: Float,
    saturation: Float,
    onPick: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val discColors = remember { hueRingColors() }
    val geo = remember(canvasSize) {
        if (canvasSize.width == 0) null
        else DiscGeo(canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }

    fun apply(point: Offset) {
        val g = geo ?: return
        onPick(g.hueAt(point), g.saturationAt(point))
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { point -> apply(point) },
                    onDrag = { change, _ ->
                        change.consume()
                        apply(change.position)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { point -> apply(point) }
            }
    ) {
        val g = geo ?: DiscGeo(size.width, size.height)

        // القرص: تدرّج الهوى (بالزاوية) + تدرّج أبيض من المركز (التشبّع)
        drawCircle(
            brush = Brush.sweepGradient(discColors, g.center),
            radius = g.radius,
            center = g.center
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                center = g.center,
                radius = g.radius
            ),
            radius = g.radius,
            center = g.center
        )
        drawCircle(
            color = Color.Gray.copy(alpha = 0.35f),
            radius = g.radius,
            center = g.center,
            style = Stroke(width = 1f)
        )

        // مقبض اللون: يتحرك بين مركز القرص وحافته حسب الزاوية والتشبّع
        val thumb = g.thumb(hue, saturation)
        val thumbRadius = g.radius * 0.085f
        drawCircle(color = Color.White, radius = thumbRadius, center = thumb)
        drawCircle(
            color = hsvToColor(hue, saturation, 1f),
            radius = thumbRadius * 0.72f,
            center = thumb
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            radius = thumbRadius,
            center = thumb,
            style = Stroke(width = 1.5f)
        )
    }
}

/** شريط السطوع: من الأسود إلى اللون النقي المختار */
@Composable
private fun BrightnessBar(
    hue: Float,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val pureHue = hsvToColor(hue, 1f, 1f)

    fun apply(x: Float) {
        if (barSize.width == 0) return
        val thumbRadius = barSize.height * 0.42f
        val travel = barSize.width - 2f * thumbRadius
        if (travel <= 1f) {
            onValueChange(0f)
            return
        }
        onValueChange(((x - thumbRadius) / travel).coerceIn(0f, 1f))
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { barSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { point -> apply(point.x) },
                    onDrag = { change, _ ->
                        change.consume()
                        apply(change.position.x)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { point -> apply(point.x) }
            }
    ) {
        val height = size.height
        val width = size.width
        val trackTop = (height - height * 0.45f) / 2f
        val trackHeight = height * 0.45f
        val radius = trackHeight / 2f

        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.Black, pureHue)),
            topLeft = Offset(0f, trackTop),
            size = Size(width, trackHeight),
            cornerRadius = CornerRadius(radius, radius)
        )
        drawRoundRect(
            color = Color.Gray.copy(alpha = 0.35f),
            topLeft = Offset(0f, trackTop),
            size = Size(width, trackHeight),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 1f)
        )

        // مقبض السطوع
        val thumbRadius = height * 0.42f
        val thumbX = thumbRadius + value * (width - 2f * thumbRadius)
        drawCircle(color = Color.White, radius = thumbRadius, center = Offset(thumbX, height / 2f))
        drawCircle(
            color = hsvToColor(hue, 1f, value),
            radius = thumbRadius * 0.7f,
            center = Offset(thumbX, height / 2f)
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            radius = thumbRadius,
            center = Offset(thumbX, height / 2f),
            style = Stroke(width = 1.5f)
        )
    }
}

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

@Composable
private fun ColorRingSquare(
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

/** حلقة قوس قزح للاستخدام كأيقونة العجلة في قائمة الألوان */
@Composable
internal fun rememberHueRingBrush(): Brush = remember { Brush.sweepGradient(hueRingColors()) }
