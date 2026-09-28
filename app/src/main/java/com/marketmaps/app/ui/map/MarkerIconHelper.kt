package com.marketmaps.app.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import com.caverock.androidsvg.SVG
import com.marketmaps.app.data.CategoryStyles
import com.marketmaps.app.util.logW
import org.mapsforge.core.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import android.graphics.Bitmap as AndroidBitmap
import org.mapsforge.map.android.graphics.AndroidBitmap as MapsforgeAndroidBitmap

/**
 * أيقونات العلامات للخريطتين.
 *
 * - الأحجام بالـ dp وتُحوَّل إلى بكسل حسب كثافة الشاشة (كانت بكسل ثابتة فتظهر
 *   صغيرة جداً على الشاشات الكثيفة وكبيرة على الضعيفة).
 * - الأيقونة تُرسم مباشرة بحجمها النهائي من الـ SVG (لا createScaledBitmap بعد الرسم
 *   الذي كان يجعلها ضبابية ويُنشئ صورة جديدة لكل محل).
 * - ملفات SVG تُحلَّل مرة واحدة، والصور الناتجة في LruCache بمفتاح
 *   (نوع|أيقونة|لون|حجم) فكل المحلات من نفس التصنيف تشارك نفس الصورة.
 * - آمنة للاستدعاء من خيط خلفي (Dispatchers.Default).
 *
 * تنبيه: الصور المخزنة مشتركة — لا تُستدعى recycle عليها ولا onDestroy على
 * Markers تستخدمها (انظر MapLayerHelper.destroy).
 */
object MarkerIconHelper {

    private const val TAG = "MarkerIconHelper"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var density: Float = 2.75f

    /** الحد بالكيلوبايت (~6MB تكفي مئات الأيقونات بأحجام مختلفة) */
    private val bitmapCache = object : LruCache<String, AndroidBitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: AndroidBitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    /** SVG محلَّل لكل ملف؛ الملفات غير الموجودة تُسجَّل حتى لا نحاول فتحها مجدداً */
    private val svgCache = ConcurrentHashMap<String, SVG>()
    private val missingSvgs = ConcurrentHashMap.newKeySet<String>()

    // ألوان قريبة من تسميات نقاط الاهتمام في خرائط جوجل (الوضع الفاتح)
    private val GOOGLE_LABEL_TEXT = Color.parseColor("#48707F")
    private const val GOOGLE_LABEL_STROKE = Color.WHITE
    private val USER_PIN_RED = Color.parseColor("#EA4335")
    private val USER_PIN_CORE = Color.parseColor("#C5221F")

    private val labelTypeface: Typeface by lazy {
        try {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        } catch (e: Exception) {
            logW(TAG, "sans-serif-medium غير متاح", e)
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    enum class DisplayMode {
        BUBBLE_LARGE,
        BUBBLE_MEDIUM,
        CIRCLE,
        HIDDEN
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        density = context.resources.displayMetrics.density
    }

    /** تفريغ الكاش (عند onTrimMemory) — الصور المستخدمة حالياً تبقى حتى يجمعها GC */
    fun clearCache() {
        bitmapCache.evictAll()
        svgCache.clear()
    }

    /** إرجاع من الكاش فقط إن كانت الصورة ما زالت صالحة (Mapsforge قد يعمل recycle للصورة المشتركة) */
    private fun cacheGet(key: String): AndroidBitmap? {
        val cached = bitmapCache.get(key) ?: return null
        if (cached.isRecycled) {
            bitmapCache.remove(key)
            return null
        }
        return cached
    }

    private fun dp(value: Float): Int = (value * density).roundToInt().coerceAtLeast(1)

    private fun dpF(value: Float): Float = value * density

    /**
     * أوضاع العرض حسب مقياس الشارع (شريط المسافة):
     * ~50م فقاعة كبيرة، ~100م متوسطة، ~200م/500قدم نقطة، أبعد = مخفي
     */
    fun displayModeForZoom(zoom: Int, latitude: Double = 30.0): DisplayMode = when {
        zoom >= 17 -> DisplayMode.BUBBLE_LARGE   // حوالي 50 متر
        zoom >= 16 -> DisplayMode.BUBBLE_MEDIUM  // حوالي 100 متر
        zoom >= 15 -> DisplayMode.CIRCLE         // حوالي 200 متر / 500 قدم
        else -> DisplayMode.HIDDEN               // أبعد من 200م (لا دوائر)
    }

    fun displayModeForGoogleZoom(zoom: Float, latitude: Double = 30.0): DisplayMode =
        displayModeForZoom(zoom.toInt().coerceIn(1, 22), latitude)

    /**
     * أحجام حسب مقياس الشارع:
     * ~50م فقاعة (+10٪ عن الحجم السابق 52→57)، ~100م متوسطة،
     * ~200م/500قدم نقطة، أبعد مخفي.
     */
    fun markerSizePxForZoom(zoom: Int): Int = when {
        zoom >= 18 -> 62   // أقرب من 50م
        zoom >= 17 -> 57   // ~50م
        zoom >= 16 -> 34   // ~100م
        zoom >= 15 -> 16   // ~200م / 500 قدم — دائرة فقط
        else -> 0          // أبعد من 200م: مخفي بالكامل
    }

    fun sizeForZoom(zoom: Int): Int = markerSizePxForZoom(zoom)

    fun markerSizePxForMode(mode: DisplayMode): Int = when (mode) {
        DisplayMode.BUBBLE_LARGE -> 57   // 50م +10٪
        DisplayMode.BUBBLE_MEDIUM -> 34  // 100م
        DisplayMode.CIRCLE -> 16         // 200م نقطة
        DisplayMode.HIDDEN -> 0
    }

    fun sizeForMode(mode: DisplayMode): Int = markerSizePxForMode(mode)

    /** أيقونة موقعي — أكبر بنسبة 30٪ عن السابق */
    private fun userPinSizePx(mode: DisplayMode): Int = when (mode) {
        DisplayMode.HIDDEN -> 52         // يبقى ظاهراً عند التصغير
        DisplayMode.CIRCLE -> 21         // 16×1.3
        DisplayMode.BUBBLE_MEDIUM -> 52  // 40×1.3
        DisplayMode.BUBBLE_LARGE -> 73   // 56×1.3
    }

    fun colorForCategory(category: String): Int = CategoryStyles.colorFor(category)

    fun iconNameForCategory(category: String): String = CategoryStyles.iconNameFor(category)

    /** صورة أيقونة التصنيف بالحجم المطلوب (من الكاش إن وُجدت) */
    fun getAndroidMarkerBitmap(
        category: String,
        mode: DisplayMode,
        sizePx: Int = markerSizePxForMode(mode)
    ): AndroidBitmap? {
        if (mode == DisplayMode.HIDDEN || sizePx <= 0) return null
        val style = CategoryStyles.styleFor(category)
        return when (mode) {
            DisplayMode.CIRCLE -> composeCircle(style.color, sizePx)
            else -> composeBubble(style.color, style.iconName, sizePx)
        }
    }

    /** غلاف Mapsforge جديد حول صورة مشتركة من الكاش */
    fun getMarkerBitmap(category: String, mode: DisplayMode, sizePx: Int): Bitmap? {
        val src = getAndroidMarkerBitmap(category, mode, sizePx) ?: return null
        if (src.isRecycled) return null
        // نسخة مستقلة: Mapsforge يستدعي recycle عند إزالة العلامة، والكاش مشترك
        val copy = src.copy(src.config ?: AndroidBitmap.Config.ARGB_8888, false) ?: return null
        return MapsforgeAndroidBitmap(copy)
    }

    fun getAndroidUserLocationBitmap(mode: DisplayMode = DisplayMode.BUBBLE_MEDIUM): AndroidBitmap? {
        return composeGoogleUserPin(userPinSizePx(mode))
    }

    fun getUserLocationBitmap(mode: DisplayMode = DisplayMode.BUBBLE_MEDIUM): Bitmap {
        val src = composeGoogleUserPin(userPinSizePx(mode))
        val copy = src.copy(src.config ?: AndroidBitmap.Config.ARGB_8888, false) ?: src
        return MapsforgeAndroidBitmap(copy)
    }

    /**
     * أيقونة + اسم بأسلوب قريب من تسميات خرائط جوجل على الخريطة الفاتحة:
     * نص داكن + حد أبيض (halo) بدون خلفية صلبة.
     * هذه الصورة خاصة بكل محل (فيها اسمه) فلا تُحفظ هنا؛ GoogleMapContent يحفظ
     * الـ BitmapDescriptor الناتج.
     */
    fun getAndroidMarkerBitmapWithLabel(
        category: String,
        name: String,
        mode: DisplayMode,
        scale: Float = 1f
    ): AndroidBitmap? {
        if (mode == DisplayMode.HIDDEN) return null
        val iconSize = (markerSizePxForMode(mode) * scale).roundToInt()
        if (mode == DisplayMode.CIRCLE) return getAndroidMarkerBitmap(category, mode, iconSize)
        val icon = getAndroidMarkerBitmap(category, mode, iconSize) ?: return null
        val label = name.trim().ifEmpty { return icon }
        val displayName = if (label.length > 22) label.take(21) + "…" else label

        val textSize = dpF(if (mode == DisplayMode.BUBBLE_LARGE) 12.5f else 10f) * scale
        val strokeWidth = dpF(if (mode == DisplayMode.BUBBLE_LARGE) 2f else 1.6f) * scale

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOOGLE_LABEL_TEXT
            this.textSize = textSize
            typeface = labelTypeface
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(fillPaint).apply {
            color = GOOGLE_LABEL_STROKE
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeJoin = Paint.Join.ROUND
            strokeMiter = 2f
        }

        val fm = fillPaint.fontMetrics
        val textWidth = fillPaint.measureText(displayName)
        val textHeight = fm.bottom - fm.top
        val gap = dpF(0.75f)
        val haloPad = strokeWidth + gap

        val width = maxOf(icon.width.toFloat(), textWidth + haloPad * 2).toInt().coerceAtLeast(1)
        val height = (icon.height + gap + textHeight + haloPad).toInt().coerceAtLeast(1)

        val out = AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(icon, (width - icon.width) / 2f, 0f, null)
        val textX = width / 2f
        val textY = icon.height + gap - fm.top
        // الحد الأبيض أولاً ثم النص الداكن فوقه
        canvas.drawText(displayName, textX, textY, strokePaint)
        canvas.drawText(displayName, textX, textY, fillPaint)
        return out
    }

    // ───────────────────────── الرسم ─────────────────────────

    private fun loadSvg(name: String): SVG? {
        svgCache[name]?.let { return it }
        if (name in missingSvgs) return null
        val ctx = appContext ?: return null
        return try {
            SVG.getFromAsset(ctx.assets, "markers/$name.svg").also { svgCache[name] = it }
        } catch (e: Exception) {
            logW(TAG, "تعذر تحميل markers/$name.svg", e)
            missingSvgs.add(name)
            null
        }
    }

    /** رسم SVG بحجم محدد ولون موحّد (SRC_IN) داخل المستطيل المعطى */
    private fun drawTintedSvg(
        canvas: Canvas,
        svg: SVG,
        left: Float,
        top: Float,
        sizePx: Int,
        color: Int
    ) {
        // الملفات كلها بـ viewBox بدون width/height، فيتم تحجيمها لتملأ المستطيل.
        // renderToPicture(w, h) لا يعدّل المستند، لكن نزامن احتياطاً لأن SVG قد يُرسم من خيوط متعددة.
        val picture = synchronized(svg) { svg.renderToPicture(sizePx, sizePx) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        canvas.save()
        canvas.translate(left, top)
        canvas.saveLayer(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), paint)
        canvas.drawPicture(picture)
        canvas.restore()
        canvas.restore()
    }

    private fun composeBubble(bubbleColor: Int, iconName: String, size: Int): AndroidBitmap {
        val cacheKey = "b|$iconName|$bubbleColor|$size"
        cacheGet(cacheKey)?.let { return it }

        val bmp = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bubbleSvg = loadSvg("icon_bubble")
        if (bubbleSvg != null) {
            drawTintedSvg(canvas, bubbleSvg, 0f, 0f, size, bubbleColor)
            val innerSvg = loadSvg(iconName) ?: loadSvg("other")
            if (innerSvg != null) {
                val iconSize = (size * 0.42f).toInt().coerceAtLeast(4)
                drawTintedSvg(canvas, innerSvg, (size - iconSize) / 2f, size * 0.18f, iconSize, Color.WHITE)
            }
        } else {
            // بديل بسيط إذا تعذر تحميل الـ SVG
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = bubbleColor
            }
            canvas.drawRoundRect(
                RectF(size * 0.05f, size * 0.05f, size * 0.95f, size * 0.78f),
                size * 0.2f, size * 0.2f, p
            )
            val tip = Path().apply {
                moveTo(size * 0.35f, size * 0.72f)
                lineTo(size * 0.65f, size * 0.72f)
                lineTo(size * 0.5f, size * 0.95f)
                close()
            }
            canvas.drawPath(tip, p)
        }
        bitmapCache.put(cacheKey, bmp)
        return bmp
    }

    private fun composeCircle(color: Int, size: Int): AndroidBitmap {
        val cacheKey = "c|$color|$size"
        cacheGet(cacheKey)?.let { return it }

        val bmp = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val circleSvg = loadSvg("circle")
        if (circleSvg != null) {
            drawTintedSvg(canvas, circleSvg, 0f, 0f, size, color)
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.color = color
            }
            canvas.drawCircle(size / 2f, size / 2f, size * 0.4f, p)
        }
        bitmapCache.put(cacheKey, bmp)
        return bmp
    }

    /** دبوس أحمر كلاسيكي بأسلوب خرائط جوجل لموقع المستخدم */
    private fun composeGoogleUserPin(size: Int): AndroidBitmap {
        val cacheKey = "u|$size"
        cacheGet(cacheKey)?.let { return it }

        val bmp = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val w = size.toFloat()
        val h = size.toFloat()
        val headCx = w / 2f
        val headCy = h * 0.38f
        val headR = w * 0.28f
        val tipY = h * 0.92f
        val path = Path().apply {
            moveTo(headCx - headR * 0.92f, headCy + headR * 0.35f)
            lineTo(headCx, tipY)
            lineTo(headCx + headR * 0.92f, headCy + headR * 0.35f)
            close()
        }
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = USER_PIN_RED
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, pinPaint)
        canvas.drawCircle(headCx, headCy, headR, pinPaint)
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(headCx, headCy, headR * 0.42f, inner)
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = USER_PIN_CORE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(headCx, headCy, headR * 0.18f, core)
        bitmapCache.put(cacheKey, bmp)
        return bmp
    }
}
