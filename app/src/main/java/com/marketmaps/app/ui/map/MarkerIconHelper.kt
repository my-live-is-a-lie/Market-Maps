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
 * أيقونات علامات — حجم كبير + موقع المستخدم بالأحمر.
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
        val key = "store_v2_$color"
        // حجم أكبر بكثير (حوالي ضعف السابق)
        return cache.getOrPut(key) { createPinBitmap(color, 168, 224) }
    }

    /** علامة الموقع الحالي — حمراء */
    fun getUserLocationBitmap(): Bitmap {
        return cache.getOrPut("user_red_v2") { createUserPinBitmap() }
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
            this.color = Color.argb(80, 0, 0, 0)
        }

        canvas.drawCircle(width / 2f + 4f, height * 0.34f + 4f, width * 0.36f, shadow)

        val headRadius = width * 0.38f
        val headCx = width / 2f
        val headCy = height * 0.32f
        canvas.drawCircle(headCx, headCy, headRadius, paint)

        val path = Path().apply {
            moveTo(headCx - headRadius * 0.78f, headCy + headRadius * 0.35f)
            lineTo(headCx + headRadius * 0.78f, headCy + headRadius * 0.35f)
            lineTo(headCx, height * 0.96f)
            close()
        }
        canvas.drawPath(path, paint)

        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = Color.WHITE
        }
        canvas.drawCircle(headCx, headCy, headRadius * 0.38f, inner)

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            this.color = Color.argb(220, 255, 255, 255)
        }
        canvas.drawCircle(headCx, headCy, headRadius - 2.5f, stroke)

        val drawable = BitmapDrawable(null, androidBmp)
        return AndroidGraphicFactory.convertToBitmap(drawable)
    }

    /** دبوس أحمر لموقع المستخدم */
    private fun createUserPinBitmap(): Bitmap {
        val width = 140
        val height = 180
        val color = Color.parseColor("#E53935")
        val androidBmp = AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(androidBmp)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = Color.argb(90, 0, 0, 0)
        }

        canvas.drawCircle(width / 2f + 3f, height * 0.34f + 3f, width * 0.34f, shadow)

        val headRadius = width * 0.36f
        val headCx = width / 2f
        val headCy = height * 0.32f
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
        canvas.drawCircle(headCx, headCy, headRadius * 0.35f, inner)

        val center = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        canvas.drawCircle(headCx, headCy, headRadius * 0.18f, center)

        val drawable = BitmapDrawable(null, androidBmp)
        return AndroidGraphicFactory.convertToBitmap(drawable)
    }
}
