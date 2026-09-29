package com.marketmaps.app.ui.map

import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.queue.Job
import org.mapsforge.map.model.common.Observer
import java.util.concurrent.ConcurrentHashMap
import com.marketmaps.app.util.logW

/**
 * غلاف لكاش بلاطات OSM الجاهزة:
 * - يُحسّن تباين الحبر الداكن في التسميات الصغيرة بشكل خفيف في الوضعين.
 * - يعيد تلوين البلاطات إلى النمط الليلي الرسمي من جوجل عند تفعيل الوضع الليلي.
 *
 * تُعالج البلاطة مرة واحدة عند تحميلها إلى الكاش، وليس على عرض الخريطة كاملاً،
 * حتى لا تتأثر ألوان علامات التطبيق وأيقوناته.
 */
class NightTileCache(
    private val delegate: TileCache,
    private val night: Boolean
) : TileCache {

    /** Keys already transformed in this session; also lazily migrates tiles from the old disk cache. */
    private val transformedKeys = ConcurrentHashMap.newKeySet<Job>()

    override fun containsKey(key: Job): Boolean = delegate.containsKey(key)

    override fun destroy() {
        transformedKeys.clear()
        delegate.destroy()
    }

    override fun get(key: Job): TileBitmap? = delegate.get(key)?.let { transformCached(key, it) }

    override fun getCapacity(): Int = delegate.getCapacity()

    override fun getCapacityFirstLevel(): Int = delegate.getCapacityFirstLevel()

    override fun getImmediately(key: Job): TileBitmap? =
        delegate.getImmediately(key)?.let { transformCached(key, it) }

    override fun purge() {
        transformedKeys.clear()
        delegate.purge()
    }

    override fun setWorkingSet(workingSet: MutableSet<Job>) = delegate.setWorkingSet(workingSet)

    override fun addObserver(observer: Observer) = delegate.addObserver(observer)

    override fun removeObserver(observer: Observer) = delegate.removeObserver(observer)

    override fun put(key: Job, bitmap: TileBitmap?) {
        if (bitmap == null) {
            transformedKeys.remove(key)
            delegate.put(key, null)
            return
        }
        // تحسين التباين يُطبّق في الوضعين، أما إعادة التلوين ففي الوضع الليلي فقط.
        val processed = transform(bitmap)
        if (processed != null) transformedKeys.add(key)
        delegate.put(key, processed ?: bitmap)
    }

    /** يحوّل أيضاً البلاطات القديمة عند استرجاعها من الكاش الدائم. */
    private fun transformCached(key: Job, bitmap: TileBitmap): TileBitmap {
        if (key in transformedKeys) return bitmap
        synchronized(transformedKeys) {
            if (key in transformedKeys) return bitmap
            val processed = transform(bitmap) ?: return bitmap
            delegate.put(key, processed)
            transformedKeys.add(key)
            return processed
        }
    }

    /** تحسين بسيط لتباين النصوص الداكنة (مع الحفاظ على ألوان الخريطة) وإعادة تلوين الليل */
    private fun transform(src: TileBitmap): TileBitmap? {
        return try {
            val androidSrc = AndroidGraphicFactory.getBitmap(src) ?: return null
            val width = androidSrc.width
            val height = androidSrc.height
            if (width <= 0 || height <= 0) return null

            val out = AndroidGraphicFactory.INSTANCE.createTileBitmap(width, androidSrc.hasAlpha())
                ?: return null
            val androidOut = AndroidGraphicFactory.getBitmap(out) ?: return null

            val pixels = IntArray(width * height)
            androidSrc.getPixels(pixels, 0, width, 0, 0, width, height)

            for (i in pixels.indices) {
                val c = pixels[i]
                val alpha = c ushr 24
                val originalR = (c shr 16) and 0xFF
                val originalG = (c shr 8) and 0xFF
                val originalB = c and 0xFF
                val spread = maxOf(originalR, originalG, originalB) - minOf(originalR, originalG, originalB)
                val originalLuma = ((originalR * 77 + originalG * 150 + originalB * 29) shr 8).coerceIn(0, 255)

                // أسماء OSM داكنة ومحايدة غالباً. نُغمّق درجات الحبر والـ anti-aliasing
                // حوله بشكل طفيف فقط؛ لا نمس الخلفيات الفاتحة أو ألوان الطرق والمياه.
                val inkLuma = if (spread < LOW_CHROMA_SPREAD && originalLuma in INK_LUMA_MIN..INK_LUMA_MAX) {
                    (originalLuma * INK_CONTRAST_FACTOR).toInt().coerceAtLeast(0)
                } else originalLuma
                val scale = if (originalLuma == 0) 1f else inkLuma.toFloat() / originalLuma
                val r = (originalR * scale).toInt().coerceIn(0, 255)
                val g = (originalG * scale).toInt().coerceIn(0, 255)
                val b = (originalB * scale).toInt().coerceIn(0, 255)
                val luma = ((r * 77 + g * 150 + b * 29) shr 8).coerceIn(0, 255)

                val packed = if (!night) {
                    (r shl 16) or (g shl 8) or b
                } else if (spread < LOW_CHROMA_SPREAD) {
                    // رمادي: أرض/شوارع/مبانٍ/حدود/نصوص ← جدول الإضاءة الرسمي
                    LUT[luma]
                } else if (originalB - originalR > BLUE_BIAS) {
                    WATER
                } else if (originalG - originalR > GREEN_BIAS) {
                    PARK
                } else if (originalR - originalB > WARM_BIAS) {
                    HIGHWAY
                } else {
                    // ملوّن غير مصنّف (بني باهت/رمادي مائل) ← مسار الأرض الرسمي
                    LUT[luma]
                }

                pixels[i] = (alpha shl 24) or packed
            }

            androidOut.setPixels(pixels, 0, width, 0, 0, width, height)
            out.setTimestamp(src.getTimestamp())
            out
        } catch (e: Exception) {
            logW(TAG, "تعذّرت معالجة بلاطة OSM", e)
            null
        }
    }

    companion object {
        private const val TAG = "NightTileCache"

        /** حدّ يُعتبر ما دونه رمادياً (أرض/حدود/شوارع/نصوص) */
        private const val LOW_CHROMA_SPREAD = 24

        /** تمييز عائلات الألوان في بلاطات Mapnik */
        private const val BLUE_BIAS = 12   // مياه: أزرق − أحمر
        private const val GREEN_BIAS = 8   // خضرة: أخضر − أحمر
        private const val WARM_BIAS = 20   // طرق سريعة/رمل: أحمر − أزرق
        // نُغمّق درجات الحواف الرمادية المحيطة بالحروف، وتبقى النتيجة دون الحد الأدنى
        // حتى لا يتكرر التحسين على البلاطات المحفوظة في الكاش الدائم.
        private const val INK_LUMA_MIN = 105
        private const val INK_LUMA_MAX = 180
        private const val INK_CONTRAST_FACTOR = 0.58f

        /** ألوان النمط الليلي الرسمي من جوجل (حِزم RGB جاهزة) */
        private const val WATER = 0x17263C     // مياه
        private const val PARK = 0x263C3F      // حدائق وخضرة
        private const val HIGHWAY = 0x746855   // طرق سريعة

        /**
         * نقاط تحكم جدول الإضاءة (إضاءة بلاطة Mapnik ← لون جوجل الرسمي):
         * 0–90 نصوص داكنة ← #9CA5B3 (تسميات الطرق في النمط الرسمي)،
         * 150 رمادي متوسط ← #746855، 205 مبانٍ/حدود ← #2B3544،
         * 240 الأرض البيج ← #242F3E، 250–255 الشوارع البيضاء ← #38414E.
         * (المُدخل، ثم R، G، B للمخرج)
         */
        private val CONTROL = intArrayOf(
            0, 156, 165, 179,
            90, 156, 165, 179,
            150, 116, 104, 85,
            205, 43, 53, 68,
            240, 36, 47, 62,
            250, 56, 65, 78,
            255, 56, 65, 78
        )

        /** جدول 256 مدخلاً (لون مُحزَم) مبني من نقاط التحكم بالاستيفاء الخطي */
        private val LUT: IntArray = IntArray(256).also { table ->
            val points = CONTROL.size / 4
            for (i in 0 until 256) {
                var p = 0
                while (p < points - 1 && CONTROL[(p + 1) * 4] < i) p++
                val x0 = CONTROL[p * 4]
                val r0 = CONTROL[p * 4 + 1]
                val g0 = CONTROL[p * 4 + 2]
                val b0 = CONTROL[p * 4 + 3]
                val x1 = CONTROL[(p + 1) * 4]
                val r1 = CONTROL[(p + 1) * 4 + 1]
                val g1 = CONTROL[(p + 1) * 4 + 2]
                val b1 = CONTROL[(p + 1) * 4 + 3]
                val span = x1 - x0
                val t = if (span <= 0) 0 else (i - x0).coerceIn(0, span)
                val r = if (span <= 0) r1 else r0 + (r1 - r0) * t / span
                val g = if (span <= 0) g1 else g0 + (g1 - g0) * t / span
                val b = if (span <= 0) b1 else b0 + (b1 - b0) * t / span
                table[i] = (r.coerceIn(0, 255) shl 16) or
                    (g.coerceIn(0, 255) shl 8) or
                    b.coerceIn(0, 255)
            }
        }
    }
}
