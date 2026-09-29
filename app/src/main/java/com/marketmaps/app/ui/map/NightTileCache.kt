package com.marketmaps.app.ui.map

import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.queue.Job
import org.mapsforge.map.model.common.Observer
import com.marketmaps.app.util.logW

/**
 * غلاف كاش يلوّن بلاطات OSM المباشرة (بلاطات Mapnik الجاهزة) في الوضع الليلي
 * بـ**النمط الليلي الرسمي من جوجل** (اللوحة المنشورة في وثائق جوجل):
 *   الأرض #242F3E — المياه #17263C — الحدائق والخضرة #263C3F
 *   الطرق العامة/المحلية #38414E — الطرق السريعة #746855
 *   إطار الطرق #212A37 (وإطار السريع #1F2835) — السكك #2F3948
 *   التسميات #9CA5B3 على هالة #242F3E — أسماء المياه #515C6D
 *
 * لماذا على الكاش وليس على عرض الخريطة؟
 * بلاطات Mapnik صور جاهزة لا يمكن إعادة تلوينها من المصدر، والحل الشائع هو فلتر
 * على عرض الخريطة كاملاً — لكن ذلك يُفسد ألوان علامات التطبيق (الأبيض داخل
 * الشعارات يصبح أسود). هنا نُلوّن البلاطات مرة واحدة عند تحميلها ودخولها للكاش.
 *
 * كيف نُلوّن؟
 * بلاطات Mapnik فاتحة: الأرض بيج، الشوارع بيضاء، النصوص داكنة، والمياه/الخضرة/الطرق
 * الرئيسية ملوّنة. لذلك:
 *   1) جدول إضاءة (LUT) للعناصر الرمادية: النصوص الداكنة ← #9CA5B3 (لون تسميات
 *      جوجل الرسمي)، الأرض البيج ← #242F3E، الشوارع البيضاء ← #38414E، والمناطق
 *      الرمادية المتوسطة (مبانٍ/حدود) ← #2B3544، فتظل الشوارع أعلى إضاءة من الأرض
 *      وحدودها أغمق منها — تماماً كترتيب النمط الرسمي.
 *   2) للعناصر الملوّنة: تحديد العائلة اللونية ثم التعيين إلى لون جوجل الرسمي
 *      المقابل (مياه/خضرة/طرق سريعة) — بلا أي لون خارج اللوحة الرسمية.
 * الكاش يحفظ البلاطات الملوَّنة، لذلك [MapLayerHelper.applyOnline] يمسح كاش
 * الأونلاين عند تغيير الوضع الليلي ليعاد التحميل بالألوان الصحيحة.
 */
class NightTileCache(
    private val delegate: TileCache,
    private val night: Boolean
) : TileCache {

    override fun containsKey(key: Job): Boolean = delegate.containsKey(key)

    override fun destroy() = delegate.destroy()

    override fun get(key: Job): TileBitmap? = delegate.get(key)

    override fun getCapacity(): Int = delegate.getCapacity()

    override fun getCapacityFirstLevel(): Int = delegate.getCapacityFirstLevel()

    override fun getImmediately(key: Job): TileBitmap? = delegate.getImmediately(key)

    override fun purge() = delegate.purge()

    override fun setWorkingSet(workingSet: MutableSet<Job>) = delegate.setWorkingSet(workingSet)

    override fun addObserver(observer: Observer) = delegate.addObserver(observer)

    override fun removeObserver(observer: Observer) = delegate.removeObserver(observer)

    override fun put(key: Job, bitmap: TileBitmap?) {
        if (bitmap == null) {
            delegate.put(key, null)
            return
        }
        delegate.put(key, if (night) recolor(bitmap) ?: bitmap else bitmap)
    }

    /** بلاطة جديدة بالنمط الليلي الرسمي من جوجل؛ تُرجع null إن تعذّر التحويل */
    private fun recolor(src: TileBitmap): TileBitmap? {
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
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                // الإضاءة وفق Rec.601
                val luma = ((r * 77 + g * 150 + b * 29) shr 8).coerceIn(0, 255)
                val spread = maxOf(r, g, b) - minOf(r, g, b)

                val packed = if (spread < LOW_CHROMA_SPREAD) {
                    // رمادي: أرض/شوارع/مبانٍ/حدود/نصوص ← جدول الإضاءة الرسمي
                    LUT[luma]
                } else if (b - r > BLUE_BIAS) {
                    WATER
                } else if (g - r > GREEN_BIAS) {
                    PARK
                } else if (r - b > WARM_BIAS) {
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
            logW(TAG, "تعذّر تلوين البلاطة للوضع الليلي", e)
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
