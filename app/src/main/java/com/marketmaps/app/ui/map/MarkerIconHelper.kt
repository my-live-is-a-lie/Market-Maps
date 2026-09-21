package com.marketmaps.app.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Bitmap as AndroidBitmap
import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

/**
 * إنشاء أيقونات علامات ملوّنة حسب نوع/تصنيف المحل.
 */
object MarkerIconHelper {

    private val cache = mutableMapOf<Int, Bitmap>()

    /**
     * لون العلامة حسب الكلمات الموجودة في التصنيف.
     */
    fun colorForCategory(category: String): Int {
        val c = category.lowercase()
        return when {
            // صحة
            listOf("صيدلية", "مستشفى", "عيادة", "أسنان").any { it in c } ->
                Color.parseColor("#E53935") // أحمر

            // طعام ومشروبات
            listOf("مطعم", "مقهى", "كافي", "وجبات", "شعبي", "أغذية").any { it in c } ->
                Color.parseColor("#FB8C00") // برتقالي

            // بقالة ومواد غذائية
            listOf("بقالة", "عطارة", "سوبر").any { it in c } ->
                Color.parseColor("#43A047") // أخضر

            // ورش وصيانة
            listOf("ورشة", "سمكرة", "نجارة", "حدادة", "ميكانيكا", "كهرباء", "سباكة").any { it in c } ->
                Color.parseColor("#8E24AA") // بنفسجي

            // تعليم
            listOf("مدرسة", "ابتدائية", "إعدادية", "ثانوية", "لغات", "مكتبة").any { it in c } ->
                Color.parseColor("#1E88E5") // أزرق

            // ملابس وأحذية
            listOf("ملابس", "أحذية").any { it in c } ->
                Color.parseColor("#EC407A") // وردي

            // أجهزة ومواد بناء
            listOf("أجهزة", "كهربائية", "منزلية", "مواد بناء", "مصنع", "بلاستيك").any { it in c } ->
                Color.parseColor("#546E7A") // رمادي مزرق

            // محل عام
            "محل" in c ->
                Color.parseColor("#00897B") // فيروزي

            else ->
                Color.parseColor("#3949AB") // أزرق غامق افتراضي
        }
    }

    /**
     * إنشاء أو استرجاع أيقونة دبوس باللون المطلوب.
     */
    fun getMarkerBitmap(category: String): Bitmap {
        val color = colorForCategory(category)
        return cache.getOrPut(color) { createPinBitmap(color) }
    }

    private fun createPinBitmap(color: Int): Bitmap {
        val width = 72
        val height = 96
        val androidBmp = AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888)
        val canvas = Canvas(androidBmp)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = Color.argb(60, 0, 0, 0)
        }

        // ظل خفيف
        canvas.drawCircle(width / 2f + 2f, height * 0.38f + 2f, width * 0.32f, shadow)

        // رأس الدبوس (دائرة)
        val headRadius = width * 0.34f
        val headCx = width / 2f
        val headCy = height * 0.36f
        canvas.drawCircle(headCx, headCy, headRadius, paint)

        // طرف الدبوس (مثلث مدبب للأسفل)
        val path = Path().apply {
            moveTo(headCx - headRadius * 0.72f, headCy + headRadius * 0.45f)
            lineTo(headCx + headRadius * 0.72f, headCy + headRadius * 0.45f)
            lineTo(headCx, height * 0.92f)
            close()
        }
        canvas.drawPath(path, paint)

        // دائرة بيضاء داخلية
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = Color.WHITE
        }
        canvas.drawCircle(headCx, headCy, headRadius * 0.42f, inner)

        // حلقة خارجية خفيفة للتحديد
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            this.color = Color.argb(180, 255, 255, 255)
        }
        canvas.drawCircle(headCx, headCy, headRadius - 1.5f, stroke)

        return AndroidGraphicFactory.convertToBitmap(androidBmp)
    }

    /**
     * نص توضيحي للون (للاستخدام في مفتاح الألوان لاحقاً إن لزم).
     */
    fun legend(): List<Pair<String, Int>> = listOf(
        "صحة / صيدلية" to Color.parseColor("#E53935"),
        "مطعم / مقهى" to Color.parseColor("#FB8C00"),
        "بقالة / عطارة" to Color.parseColor("#43A047"),
        "ورشة / صيانة" to Color.parseColor("#8E24AA"),
        "مدرسة / تعليم" to Color.parseColor("#1E88E5"),
        "ملابس" to Color.parseColor("#EC407A"),
        "أجهزة / بناء" to Color.parseColor("#546E7A"),
        "محل" to Color.parseColor("#00897B"),
        "أخرى" to Color.parseColor("#3949AB")
    )
}
