package com.marketmaps.app.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.marketmaps.app.ui.theme.selectionBorder
import org.json.JSONObject

/**
 * قسم «شعار التطبيق» في مظهر التطبيق:
 * اختيار الشعار (من ملفات SVG في assets/launcher) واختيار لون خلفيته بشكل مستقل
 * (من قائمة الألوان الجاهزة أو بعجلة الألوان)، مع معاينة مباشرة.
 *
 * الشعارات والألوان تُقرأ من فهرس يولّده البناء (assets/launcher_icons.json)،
 * فإضافة شعار جديد = ملف SVG في المجلد فقط، وإضافة لون = سطر في مهمة البناء.
 */

/**
 * نسبة لوحة الأيقونة إلى المساحة المرئية في المشغّل: لوحة الأيقونة التكيفية
 * 108dp والنظام يقتصر على ~72dp مرئية، لذا نعرض اللوحة بحجم 1.5× الصندوق
 * (والصندوق يقصّ الزائد) فيرى المستخدم في المعاينة نفس ما سيراه في المشغّل تماماً.
 */
private const val ICON_CANVAS_RATIO = 1.5f

/** مفتاح اللون المخصص في التفضيلات (ترحيل الإعدادات القديمة فقط) */
const val CUSTOM_PREFIX = "custom:"

data class AppLogoOption(val key: String, val file: String, val label: String)

data class AppLogoBackground(val key: String, val label: String, val color: Color)

/** ألوان احتياطية إن لم يوجد فهرس البناء (نفس قائمة مهمة التوليد) */
private val fallbackBackgrounds = listOf(
    AppLogoBackground("blue", "أزرق", Color(0xFF024EE7)),
    AppLogoBackground("teal", "أخضر مزرق", Color(0xFF00897B)),
    AppLogoBackground("green", "أخضر", Color(0xFF43A047)),
    AppLogoBackground("orange", "برتقالي", Color(0xFFFB8C00)),
    AppLogoBackground("red", "أحمر", Color(0xFFE53935)),
    AppLogoBackground("purple", "بنفسجي", Color(0xFF8E24AA)),
    AppLogoBackground("indigo", "نيلي", Color(0xFF3949AB)),
    AppLogoBackground("dark", "أسود", Color(0xFF111111))
)

/** يقرأ الشعارات والألوان المولَّدة وقت البناء، مع احتياط إن لم يوجد الفهرس */
fun loadAppLogoCatalog(context: Context): Pair<List<AppLogoOption>, List<AppLogoBackground>> {
    val logos = mutableListOf<AppLogoOption>()
    val backgrounds = mutableListOf<AppLogoBackground>()
    try {
        val json = context.assets.open("launcher_icons.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        root.optJSONArray("logos")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val key = o.optString("key")
                if (key.isBlank()) continue
                logos += AppLogoOption(
                    key = key,
                    file = o.optString("file").ifBlank { "$key.svg" },
                    label = o.optString("label").ifBlank { key }
                )
            }
        }
        root.optJSONArray("backgrounds")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val key = o.optString("key")
                val hex = o.optString("hex")
                if (key.isBlank() || hex.isBlank()) continue
                backgrounds += AppLogoBackground(
                    key = key,
                    label = o.optString("label").ifBlank { key },
                    color = parseHexColor(hex) ?: Color.Gray
                )
            }
        }
    } catch (_: Exception) {
        // لا فهرس: نكمل بالاحتياط أدناه
    }

    if (logos.isEmpty()) {
        val files = runCatching { context.assets.list("launcher")?.toList() }.getOrNull().orEmpty()
        files.filter { it.endsWith(".svg", ignoreCase = true) }
            .sorted()
            .forEach { file ->
                val key = file.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9_]"), "_")
                logos += AppLogoOption(key, file, file.substringBeforeLast('.'))
            }
    }
    if (backgrounds.isEmpty()) backgrounds += fallbackBackgrounds
    return logos to backgrounds
}

private fun parseHexColor(hex: String): Color? = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    null
}

/** لون الخلفية المختار: إما من القائمة الجاهزة وإما لون مخصص */
fun resolveLogoBackground(value: String, backgrounds: List<AppLogoBackground>): Color = when {
    value.startsWith(CUSTOM_PREFIX) ->
        parseHexColor(value.removePrefix(CUSTOM_PREFIX)) ?: fallbackBackgrounds.first().color
    else -> backgrounds.find { it.key == value }?.color ?: fallbackBackgrounds.first().color
}

private fun loadSvgBitmap(context: Context, assetPath: String, sizePx: Int): Bitmap? = try {
    val svg = SVG.getFromAsset(context.assets, assetPath)
    svg.setDocumentWidth(sizePx.toFloat())
    svg.setDocumentHeight(sizePx.toFloat())
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    svg.renderToCanvas(Canvas(bitmap))
    bitmap
} catch (_: Exception) {
    null
}

/** معاينة الشعار على خلفيته المختارة */
@Composable
fun AppLogoPreview(
    logo: AppLogoOption?,
    backgroundColor: Color,
    boxSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val logoSizePx = with(density) { (boxSize * ICON_CANVAS_RATIO).roundToPx() }
    val bitmap = remember(logo?.file, logoSizePx) {
        logo?.let { loadSvgBitmap(context, "launcher/${it.file}", logoSizePx) }
    }
    val corner = boxSize * 0.22f
    Box(
        modifier = modifier
            .size(boxSize)
            .clip(RoundedCornerShape(corner))
            .background(backgroundColor)
            .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(boxSize * ICON_CANVAS_RATIO),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * قسم إعداد شعار التطبيق كاملاً: معاينة + لون الخلفية + اختيار الشعار.
 */
@Composable
fun AppLogoSection(
    logos: List<AppLogoOption>,
    backgrounds: List<AppLogoBackground>,
    selectedLogoKey: String,
    backgroundValue: String,
    accentColor: Color,
    onSelectLogo: (AppLogoOption) -> Unit,
    onSelectBackground: (String) -> Unit,
    launcherNote: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val logo = logos.find { it.key == selectedLogoKey } ?: logos.firstOrNull()
    // الألوان الجاهزة فقط لهذه الخلفية (لا لون مخصص)
    val backgroundColor = resolveLogoBackground(backgroundValue, backgrounds)
    val customSelected = false

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "اختر الشعار ولون خلفيته — يُستخدم الشعار كأيقونة للتطبيق",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppLogoPreview(
                    logo = logo,
                    backgroundColor = backgroundColor,
                    boxSize = 96.dp
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "معاينة الشعار",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (logo != null) logo.label else "لا يوجد شعار",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Text(text = "لون خلفية الشعار", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                backgrounds.forEach { background ->
                    val selected = !customSelected && background.key == backgroundValue
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(background.color, CircleShape)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) selectionBorder(background.color) else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { onSelectBackground(background.key) }
                    )
                }
            }

            if (logos.size > 1) {
                Text(text = "اختر الشعار", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    logos.forEach { option ->
                        val selected = option.key == logo?.key
                        AppLogoPreview(
                            logo = option,
                            backgroundColor = scheme.surfaceContainerHighest,
                            boxSize = 56.dp,
                            modifier = Modifier
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) selectionBorder(accentColor) else Color.Gray,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectLogo(option) }
                        )
                    }
                }
            }

            // توضيح ما سيحدث لأيقونة الشاشة الرئيسية (قيود نظام أندرويد)
            Text(
                text = launcherNote,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
