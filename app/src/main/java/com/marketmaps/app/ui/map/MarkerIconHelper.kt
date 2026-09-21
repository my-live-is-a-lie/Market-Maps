package com.marketmaps.app.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap as AndroidBitmap
import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

/**
 * أيقونات علامات ملوّنة — حجم أكبر لسهولة الرؤية.
 */
object MarkerIconHelper {

    private val cache = mutableMapOf<String, Bitmap>()

    fun colorForCategory(category: String): Int {
        val c = category.lowercase()
        return when {
            listOf("صيدلية", "مستشفى", "عيادة", "أسنان").any { it in c } ->
                Color.parseColor("#E53935")
            listOf("مطعم", "مقهى", "كافي", "وجبات", "شعبي", "أغذية").any { it in c } ->
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
            "محل" in c ->
                Color.parseColor("#00897B")
            else ->
                Color.parseColor("#3949AB")
        }
    }

    fun getMarkerBitmap(category: String): Bitmap {
        val color = colorForCategory(category)
        val key = "store_$color"
        return cache.getOrPut(key) { createPinBitmap(color, 120, 160) }
    }

    /** علامة الموقع الحالي (أزرق) */
    fun getUserLocationBitmap(): Bitmap {
        return cache.getOrPut("user") { createUserDotBitmap() }
    }

    private fun createPinBitmap(color: Int, width: Int, height: Int): Bitmap {
        val androidBmp = AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(androidBmp)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = Color.argb(70, 0, 0, 0)
        }

        canvas.drawCircle(width / 2f + 3f, height * 0.36f + 3f, width * 0.34f, shadow)

        val headRadius = width * 0.36f
        val headCx = width / 2f
        val headCy = height * 0.34f
        canvas.drawCircle(headCx, headCy, headRadius, paint)

        val path = Path().apply {
            moveTo(headCx - headRadius * 0.75f, headCy + headRadius * 0.4f)
            lineTo(headCx + headRadius * 0.75f, headCy + headRadius * 0.4f)
            lineTo(headCx, height * 0.95f)
            close()
        }
        canvas.drawPath(path, paint)

        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = Color.WHITE
        }
        canvas.drawCircle(headCx, headCy, headRadius * 0.4f, inner)

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            this.color = Color.argb(200, 255, 255, 255)
        }
        canvas.drawCircle(headCx, headCy, headRadius - 2f, stroke)

        val drawable = BitmapDrawable(null, androidBmp)
        return AndroidGraphicFactory.convertToBitmap(drawable)
    }

    private fun createUserDotBitmap(): Bitmap {
        val size = 64
        val androidBmp = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(androidBmp)
        val cx = size / 2f
        val cy = size / 2f

        // هالة شفافة
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(60, 33, 150, 243)
        }
        canvas.drawCircle(cx, cy, size * 0.48f, halo)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.WHITE
        }
        canvas.drawCircle(cx, cy, size * 0.28f, ring)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#2196F3")
        }
        canvas.drawCircle(cx, cy, size * 0.22f, fill)

        val drawable = BitmapDrawable(null, androidBmp)
        return AndroidGraphicFactory.convertToBitmap(drawable)
    }
}
