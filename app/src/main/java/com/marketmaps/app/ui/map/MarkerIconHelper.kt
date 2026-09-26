package com.marketmaps.app.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Bitmap as AndroidBitmap
import android.graphics.RectF
import com.caverock.androidsvg.SVG
import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.map.android.graphics.AndroidBitmap as MapsforgeAndroidBitmap
import kotlin.math.cos
import kotlin.math.pow

/**
 * أيقونات مخصصة بحجم ديناميكي حسب مستوى التكبير / مقياس المسافة.
 * الأحجام مصممة لتكون قريبة من حجم أيقونات خرائط جوجل.
 */
object MarkerIconHelper {

    private var appContext: Context? = null

    private val HEALTH_COLOR = Color.parseColor("#E53935")

    // لون نص تسميات جوجل تقريباً
    // ألوان قريبة من تسميات نقاط الاهتمام في خرائط جوجل (الوضع الفاتح)
    private val GOOGLE_LABEL_TEXT = Color.parseColor("#48707F")
    private val GOOGLE_LABEL_STROKE = Color.parseColor("#FFFFFF")

    enum class DisplayMode {
        BUBBLE_LARGE,
        BUBBLE_MEDIUM,
        CIRCLE,
        HIDDEN
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun metersPerPixel(latitude: Double, zoom: Int): Double {
        val latRad = Math.toRadians(latitude)
        return 156543.03392 * cos(latRad) / 2.0.pow(zoom.toDouble())
    }

    fun displayModeForZoom(zoom: Int, latitude: Double = 30.0): DisplayMode {
        return when {
            zoom >= 16 -> DisplayMode.BUBBLE_LARGE
            zoom >= 14 -> DisplayMode.BUBBLE_MEDIUM
            zoom >= 12 -> DisplayMode.CIRCLE
            else -> DisplayMode.HIDDEN
        }
    }

    /** حجم الأيقونة حسب الزوم بخطوات متقاربة لانتقال أنعم. */
    fun sizeForZoom(zoom: Int): Int {
        return when {
            zoom >= 18 -> 88
            zoom >= 17 -> 80
            zoom >= 16 -> 72
            zoom >= 15 -> 64
            zoom >= 14 -> 56
            zoom >= 13 -> 44
            zoom >= 12 -> 32
            zoom >= 11 -> 22
            else -> 0
        }
    }

    fun sizeForMode(mode: DisplayMode): Int {
        return when (mode) {
            DisplayMode.BUBBLE_LARGE -> 80
            DisplayMode.BUBBLE_MEDIUM -> 56
            DisplayMode.CIRCLE -> 28
            DisplayMode.HIDDEN -> 0
        }
    }

    fun colorForCategory(category: String): Int {
        val c = category.lowercase()
        return when {
            listOf("صيدلية", "مستشفى", "عيادة", "مختبر", "أسنان").any { it in c } ->
                HEALTH_COLOR
            listOf("مطعم", "مقهى", "كافي", "وجبات", "شعبي", "أغذية", "restaurant").any { it in c } ->
                Color.parseColor("#FB8C00")
            listOf("بقالة", "عطارة", "سوبر").any { it in c } ->
                Color.parseColor("#43A047")
            listOf("ورشة", "سمكرة", "نجارة", "حدادة", "ميكانيكا", "كهرباء", "سباكة").any { it in c } ->
                Color.parseColor("#8E24AA")
            listOf("مدرسة", "ابتدائية", "إعدادية", "ثانوية", "لغات", "مكتبة").any { it in c } ->
                Color.parseColor("#1E88E5")
            listOf("ملابس", "أحذية").any { it in c } ->
                Color.parseColor("#EC407A")
            listOf("أجهزة", "كهربائية", "منزلية", "مواد بناء", "مصنع", "بلاستيك").any { it in c } ->
                Color.parseColor("#546E7A")
            "محل" in c || "store" in c ->
                Color.parseColor("#00897B")
            else ->
                Color.parseColor("#3949AB")
        }
    }

    fun iconNameForCategory(category: String): String {
        val c = category.lowercase()
        return when {
            "مستشفى" in c -> "hospital"
            "عيادة" in c || "أسنان" in c -> "clinic"
            "مختبر" in c -> "lab"
            listOf("ورشة", "سمكرة", "نجارة", "حدادة", "ميكانيكا", "كهرباء", "سباكة").any { it in c } ->
                "workshop"
            listOf("مصنع", "بلاستيك").any { it in c } ->
                "factory"
            listOf("مدرسة", "ابتدائية", "إعدادية", "ثانوية", "لغات").any { it in c } ->
                "school"
            listOf("مطعم", "وجبات", "شعبي", "أغذية").any { it in c } ->
                "restaurant"
            listOf("مقهى", "كافي").any { it in c } ->
                "coffee_shop"
            listOf("محل", "بقالة", "عطارة", "صيدلية", "ملابس", "أحذية", "مكتبة",
                "أدوات", "أجهزة", "مواد", "سوبر").any { it in c } ->
                "store"
            else ->
                "other"
        }
    }

    fun getMarkerBitmap(category: String, mode: DisplayMode): Bitmap? {
        if (mode == DisplayMode.HIDDEN) return null
        val color = colorForCategory(category)
        val size = sizeForMode(mode)
        val androidBmp = when (mode) {
            DisplayMode.CIRCLE -> composeCircle(color, size)
            else -> composeBubble(color, iconNameForCategory(category), size)
        }
        return MapsforgeAndroidBitmap(androidBmp)
    }

    fun getMarkerBitmap(category: String): Bitmap {
        return getMarkerBitmap(category, DisplayMode.BUBBLE_LARGE)
            ?: MapsforgeAndroidBitmap(composeCircle(colorForCategory(category), 20))
    }

    fun getAndroidUserLocationBitmap(mode: DisplayMode = DisplayMode.BUBBLE_MEDIUM): AndroidBitmap? {
        if (mode == DisplayMode.HIDDEN) return null
        val size = when (mode) {
            DisplayMode.CIRCLE -> 28
            DisplayMode.BUBBLE_MEDIUM -> 64
            DisplayMode.BUBBLE_LARGE -> 88
            DisplayMode.HIDDEN -> 0
        }
        return composeGoogleUserPin(size)
    }

    fun getUserLocationBitmap(mode: DisplayMode = DisplayMode.BUBBLE_MEDIUM): Bitmap {
        val size = when (mode) {
            DisplayMode.HIDDEN, DisplayMode.CIRCLE -> 28
            DisplayMode.BUBBLE_MEDIUM -> 64
            DisplayMode.BUBBLE_LARGE -> 88
        }
        return MapsforgeAndroidBitmap(composeGoogleUserPin(size))
    }

    /** دبوس أحمر كلاسيكي بأسلوب خرائط جوجل لموقع المستخدم */
    private fun composeGoogleUserPin(size: Int): AndroidBitmap {
        val bmp = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val w = size.toFloat()
        val h = size.toFloat()
        // رأس الدبوس (دائرة)
        val headCx = w / 2f
        val headCy = h * 0.38f
        val headR = w * 0.28f
        // طرف الدبوس السفلي
        val tipY = h * 0.92f
        val path = android.graphics.Path().apply {
            moveTo(headCx - headR * 0.92f, headCy + headR * 0.35f)
            lineTo(headCx, tipY)
            lineTo(headCx + headR * 0.92f, headCy + headR * 0.35f)
            close()
        }
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EA4335") // أحمر جوجل
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, pinPaint)
        canvas.drawCircle(headCx, headCy, headR, pinPaint)
        // دائرة داخلية بيضاء
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(headCx, headCy, headR * 0.42f, inner)
        // نقطة مركزية زرقاء فاتحة (أسلوب جوجل أحياناً أحمر فقط؛ نستخدم أحمر غامق للنقطة)
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C5221F")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(headCx, headCy, headR * 0.18f, core)
        return bmp
    }

    /** نسخة Android Bitmap للاستخدام مع خرائط جوجل */
    fun getAndroidMarkerBitmap(category: String, mode: DisplayMode): AndroidBitmap? {
        if (mode == DisplayMode.HIDDEN) return null
        val color = colorForCategory(category)
        val size = sizeForMode(mode)
        return when (mode) {
            DisplayMode.CIRCLE -> composeCircle(color, size)
            else -> composeBubble(color, iconNameForCategory(category), size)
        }
    }

    /**
     * أيقونة + اسم بأسلوب قريب من تسميات خرائط جوجل على الخريطة الفاتحة:
     * نص داكن + حد أبيض سميك (halo) بدون خلفية بيضاء صلبة.
     * لا يمكن نسخ خط جوجل الحصري، لكن Sans-serif/Roboto هو الأقرب على أندرويد.
     */
    fun getAndroidMarkerBitmapWithLabel(
        category: String,
        name: String,
        mode: DisplayMode
    ): AndroidBitmap? {
        if (mode == DisplayMode.HIDDEN) return null
        if (mode == DisplayMode.CIRCLE) {
            return getAndroidMarkerBitmap(category, mode)
        }
        val icon = getAndroidMarkerBitmap(category, mode) ?: return null
        val label = name.trim().ifEmpty { return icon }
        val displayName = if (label.length > 22) label.take(21) + "…" else label

        val textSize = when (mode) {
            DisplayMode.BUBBLE_LARGE -> 34f
            else -> 28f
        }
        // سماكة الحد الأبيض حول الحروف (مظهر جوجل)
        val strokeWidth = when (mode) {
            DisplayMode.BUBBLE_LARGE -> 5.5f
            else -> 4.5f
        }

        val typeface = try {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
                ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        } catch (_: Exception) {
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOOGLE_LABEL_TEXT
            this.textSize = textSize
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOOGLE_LABEL_STROKE
            this.textSize = textSize
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeJoin = Paint.Join.ROUND
            strokeMiter = 2f
        }

        val fm = fillPaint.fontMetrics
        val textWidth = fillPaint.measureText(displayName)
        val textHeight = fm.bottom - fm.top
        // مساحة إضافية للحد الأبيض حول النص
        val haloPad = strokeWidth + 2f
        val gap = 2f

        val width = maxOf(icon.width.toFloat(), textWidth + haloPad * 2).toInt().coerceAtLeast(1)
        val height = (icon.height + gap + textHeight + haloPad).toInt().coerceAtLeast(1)

        val out = AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val iconLeft = (width - icon.width) / 2f
        canvas.drawBitmap(icon, iconLeft, 0f, null)

        val textX = width / 2f
        val textY = icon.height + gap - fm.top
        // الحد الأبيض أولاً ثم النص الداكن فوقه
        canvas.drawText(displayName, textX, textY, strokePaint)
        canvas.drawText(displayName, textX, textY, fillPaint)

        return out
    }

    fun displayModeForGoogleZoom(zoom: Float, latitude: Double = 30.0): DisplayMode {
        return displayModeForZoom(zoom.toInt().coerceIn(1, 22), latitude)
    }

    private fun composeBubble(bubbleColor: Int, iconName: String, size: Int): AndroidBitmap {
        val bmp = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val ctx = appContext

        if (ctx != null) {
            try {
                val bubbleSvg = SVG.getFromAsset(ctx.assets, "markers/icon_bubble.svg")
                bubbleSvg.setDocumentWidth(size.toFloat())
                bubbleSvg.setDocumentHeight(size.toFloat())
                val bubblePic = bubbleSvg.renderToPicture()
                val temp = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
                val tempCanvas = Canvas(temp)
                tempCanvas.drawPicture(bubblePic)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.colorFilter = PorterDuffColorFilter(bubbleColor, PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(temp, 0f, 0f, paint)
                temp.recycle()

                val innerName = if (assetExists(ctx, "markers/$iconName.svg")) iconName else "other"
                val iconSvg = SVG.getFromAsset(ctx.assets, "markers/$innerName.svg")
                val iconSize = (size * 0.42f).toInt().coerceAtLeast(8)
                iconSvg.setDocumentWidth(iconSize.toFloat())
                iconSvg.setDocumentHeight(iconSize.toFloat())
                val iconPic = iconSvg.renderToPicture()
                val iconBmp = AndroidBitmap.createBitmap(iconSize, iconSize, AndroidBitmap.Config.ARGB_8888)
                val iconCanvas = Canvas(iconBmp)
                iconCanvas.drawPicture(iconPic)
                val whitePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                whitePaint.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                val left = (size - iconSize) / 2f
                val top = size * 0.18f
                canvas.drawBitmap(iconBmp, left, top, whitePaint)
                iconBmp.recycle()

                return bmp
            } catch (_: Exception) {
            }
        }

        // fallback بسيط إذا فشل تحميل الـ SVG
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = bubbleColor
        }
        canvas.drawRoundRect(RectF(size * 0.05f, size * 0.05f, size * 0.95f, size * 0.78f), size * 0.2f, size * 0.2f, p)
        val tip = android.graphics.Path().apply {
            moveTo(size * 0.35f, size * 0.72f)
            lineTo(size * 0.65f, size * 0.72f)
            lineTo(size * 0.5f, size * 0.95f)
            close()
        }
        canvas.drawPath(tip, p)
        return bmp
    }

    private fun composeCircle(color: Int, size: Int): AndroidBitmap {
        val bmp = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val ctx = appContext

        if (ctx != null) {
            try {
                val circleSvg = SVG.getFromAsset(ctx.assets, "markers/circle.svg")
                circleSvg.setDocumentWidth(size.toFloat())
                circleSvg.setDocumentHeight(size.toFloat())
                val pic = circleSvg.renderToPicture()
                val temp = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
                val tempCanvas = Canvas(temp)
                tempCanvas.drawPicture(pic)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(temp, 0f, 0f, paint)
                temp.recycle()
                return bmp
            } catch (_: Exception) {
            }
        }

        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            this.color = color
        }
        canvas.drawCircle(size / 2f, size / 2f, size * 0.4f, p)
        return bmp
    }

    private fun assetExists(context: Context, path: String): Boolean {
        return try {
            context.assets.open(path).close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
