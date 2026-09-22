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

/**
 * أيقونات مخصصة: فقاعة ملوّنة + رمز أبيض حسب نوع المكان.
 * الملفات في assets/markers/
 */
object MarkerIconHelper {

    private const val SIZE = 192
    private var appContext: Context? = null

    /** اللون المشترك للصحة: مستشفى / عيادة / مختبر / صيدلية / أسنان */
    private val HEALTH_COLOR = Color.parseColor("#E53935")

    /** يجب استدعاؤها مرة عند بدء الشاشة */
    fun init(context: Context) {
        appContext = context.applicationContext
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

    /** اسم ملف الأيقونة الداخلية حسب التصنيف */
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

    fun getMarkerBitmap(category: String): Bitmap {
        val color = colorForCategory(category)
        val iconName = iconNameForCategory(category)
        val androidBmp = composeMarker(color, iconName)
        return MapsforgeAndroidBitmap(androidBmp)
    }

    fun getUserLocationBitmap(): Bitmap {
        val androidBmp = composeMarker(Color.parseColor("#E53935"), "home")
        return MapsforgeAndroidBitmap(androidBmp)
    }

    private fun composeMarker(bubbleColor: Int, iconName: String): AndroidBitmap {
        val bmp = AndroidBitmap.createBitmap(SIZE, SIZE, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val ctx = appContext

        if (ctx != null) {
            try {
                val bubbleSvg = SVG.getFromAsset(ctx.assets, "markers/icon_bubble.svg")
                bubbleSvg.setDocumentWidth(SIZE.toFloat())
                bubbleSvg.setDocumentHeight(SIZE.toFloat())
                val bubblePic = bubbleSvg.renderToPicture()
                val temp = AndroidBitmap.createBitmap(SIZE, SIZE, AndroidBitmap.Config.ARGB_8888)
                val tempCanvas = Canvas(temp)
                tempCanvas.drawPicture(bubblePic)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.colorFilter = PorterDuffColorFilter(bubbleColor, PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(temp, 0f, 0f, paint)
                temp.recycle()

                val innerName = if (assetExists(ctx, "markers/$iconName.svg")) iconName else "other"
                val iconSvg = SVG.getFromAsset(ctx.assets, "markers/$innerName.svg")
                val iconSize = (SIZE * 0.42f).toInt()
                iconSvg.setDocumentWidth(iconSize.toFloat())
                iconSvg.setDocumentHeight(iconSize.toFloat())
                val iconPic = iconSvg.renderToPicture()
                val iconBmp = AndroidBitmap.createBitmap(iconSize, iconSize, AndroidBitmap.Config.ARGB_8888)
                val iconCanvas = Canvas(iconBmp)
                iconCanvas.drawPicture(iconPic)
                val whitePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                whitePaint.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                val left = (SIZE - iconSize) / 2f
                val top = SIZE * 0.18f
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
        canvas.drawRoundRect(RectF(SIZE * 0.05f, SIZE * 0.05f, SIZE * 0.95f, SIZE * 0.78f), 40f, 40f, p)
        val tip = android.graphics.Path().apply {
            moveTo(SIZE * 0.35f, SIZE * 0.72f)
            lineTo(SIZE * 0.65f, SIZE * 0.72f)
            lineTo(SIZE * 0.5f, SIZE * 0.95f)
            close()
        }
        canvas.drawPath(tip, p)
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
