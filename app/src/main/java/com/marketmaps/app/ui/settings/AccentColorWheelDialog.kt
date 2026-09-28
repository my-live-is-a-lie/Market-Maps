package com.marketmaps.app.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** ألوان القرص (تدرّج كامل مع إعادة اللون الأول لإغلاق الدائرة بسلاسة) */
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
 * عجلة اختيار الألوان: قرص ملوّن بالكامل (Hue من الزاوية وSaturation من المسافة عن المركز)
 * + شريط السطوع + معاينة اللون وكوده مع زر نسخ.
 */
@Composable
fun AccentColorWheelDialog(
    initialColor: Color,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عجلة اختيار الألوان") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                            text = "اللون المختار (اضغط نسخ لنسخ الرمز)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = hex,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    // نسخ رمز اللون إلى الحافظة
                    TextButton(
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
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
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
        // إطار خفيف يفصل القرص عن الخلفية
        drawCircle(
            color = Color.Gray.copy(alpha = 0.35f),
            radius = g.radius,
            center = g.center,
            style = Stroke(width = 1f)
        )

        // مقبض اللون: يتحرك بين مركز القرص وحافته حسب الزاوية والتشبّع
        val thumb = g.thumb(hue, saturation)
        val thumbRadius = g.radius * 0.085f
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = thumb
        )
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
            size = androidx.compose.ui.geometry.Size(width, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )
        drawRoundRect(
            color = Color.Gray.copy(alpha = 0.35f),
            topLeft = Offset(0f, trackTop),
            size = androidx.compose.ui.geometry.Size(width, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            style = Stroke(width = 1f)
        )

        // مقبض السطوع
        val thumbRadius = height * 0.42f
        val thumbX = thumbRadius + value * (width - 2f * thumbRadius)
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = Offset(thumbX, height / 2f)
        )
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

/** حلقة قوس قزح للاستخدام كأيقونة العجلة في قائمة الألوان */
@Composable
internal fun rememberHueRingBrush(): Brush = remember { Brush.sweepGradient(hueRingColors()) }
