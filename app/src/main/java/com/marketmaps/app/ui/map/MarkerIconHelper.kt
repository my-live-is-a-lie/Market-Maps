package com.marketmaps.app.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
 */
object MarkerIconHelper {

    private var appContext: Context? = null

    private val HEALTH_COLOR = Color.parseColor("#E53935")

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
        val approxScaleMeters = metersPerPixel(latitude, zoom) * 100.0
        return when {
            approxScaleMeters <= 70 -> DisplayMode.BUBBLE_LARGE
            approxScaleMeters <= 140 -> DisplayMode.BUBBLE_MEDIUM
            approxScaleMeters <= 280 -> DisplayMode.CIRCLE
            else -> DisplayMode.HIDDEN
        }
    }

    /** أحجام ديناميكية: 50م أكبر بنسبة ~15% إضافية */
    fun sizeForMode(mode: DisplayMode): Int {
        return when (mode) {
            DisplayMode.BUBBLE_LARGE -> 67   // ~50م (+15% عن 58)
            DisplayMode.BUBBLE_MEDIUM -> 34  // ~100م
            DisplayMode.CIRCLE -> 14
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
            ?: MapsforgeAndroidBitmap(composeCircle(colorForCategory(category), 14))
    }

    fun getUserLocationBitmap(mode: DisplayMode = DisplayMode.BUBBLE_MEDIUM): Bitmap {
        val size = when (mode) {
            DisplayMode.HIDDEN, DisplayMode.CIRCLE -> 16
            DisplayMode.BUBBLE_MEDIUM -> 34
            DisplayMode.BUBBLE_LARGE -> 53
        }
        val androidBmp = if (mode == DisplayMode.CIRCLE || mode == DisplayMode.HIDDEN) {
            composeCircle(Color.parseColor("#E53935"), size)
        } else {
            composeBubble(Color.parseColor("#E53935"), "home", size)
        }
        return MapsforgeAndroidBitmap(androidBmp)
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
                val iconSize = (size * 0.42f).toInt().coerceAtLeast(6)
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
