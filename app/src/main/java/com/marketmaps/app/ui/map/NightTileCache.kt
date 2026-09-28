package com.marketmaps.app.ui.map

import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.queue.Job
import org.mapsforge.map.model.common.Observer
import com.marketmaps.app.util.logW

/**
 * غلاف كاش يلوّن بلاطات OSM المباشرة (بلاطات Mapnik الجاهزة) في الوضع الليلي.
 *
 * لماذا على الكاش وليس على عرض الخريطة؟
 * بلاطات OSM صور جاهزة من الخادم ولا يمكن إعادة تلوينها من المصدر، والحل الشائع
 * هو فلتر على عرض الخريطة كاملاً — لكن ذلك يُفسد ألوان علامات التطبيق نفسها
 * (الأبيض داخل الشعارات يصبح أسود والأخضر يتحول إلى وردي).
 * هنا نُلوّن البلاطات مرة واحدة عند تحميلها ودخولها للكاش، فتبقى العلامات
 * (ورموزها البيضاء) بألوانها الصحيحة فوق خريطة ليلية.
 *
 * كيف نُلوّن؟ (مهم)
 * الحل المعتاد «عكس الألوان + تدوير الدرجة 180°» يعطي خريطة قاتمة، والأسوأ أن
 * بلاطات Mapnik ترسم الشوارع «الأفتح في الصورة» مع حدود (casing) رمادية حولها،
 * فينقلب الترتيب بعد العكس: شوارع سوداء تحيط بها حدود فاتحة — مظهر غير طبيعي.
 * لذلك نستخدم جدول إضاءة (LUT) يُطبَّق على الإضاءة فقط مع الحفاظ على اللون الأصلي
 * (chroma) لكل بكسل:
 *   - الشوارع (الأفتح في الصورة)  ← فاتحة
 *   - حدود الشوارع والمسارات (رمادي متوسط) ← رمادي أغمق من الشارع
 *   - الأرض والمزارع (فاتحة) ← داكنة بلون ليلي مريح
 *   - النصوص والأسماء (داكنة) ← فاتحة
 *   - المياه والخضرة تحتفظ بلونها (أزرق/أخضر) لأننا نحفظ الفرق اللوني كما هو.
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

    /** بلاطة جديدة بالألوان الليلية؛ تُرجع null إن تعذّر التحويل (فتُستخدم الأصلية) */
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
                // الإضاءة وفق Rec.601 ثم جدول اللون الليلي
                val luma = ((r * 77 + g * 150 + b * 29) shr 8).coerceIn(0, 255)
                val newLuma = LUT[luma]
                val spread = maxOf(r, g, b) - minOf(r, g, b)
                val nr: Int
                val ng: Int
                val nb: Int
                if (spread < LOW_CHROMA_SPREAD) {
                    // رمادي/أرض/حدود: نُبقي اللون قريباً من الرمادي مع صبغة ليلية
                    // باردة بسيطة بدل الاصفرار (أرض Mapnik بيج فاتحة، ولو حفظنا
                    // لونها بدت بُنّية قاتمة)
                    nr = newLuma + (r - luma) - COOL_R
                    ng = newLuma + (g - luma)
                    nb = newLuma + (b - luma) + COOL_B
                } else {
                    // مياه/خضرة/طرق رئيسية: نحفظ اللون ونقوّيه — بلاطات Mapnik قليلة
                    // التشبّع، فبلا التقوية تظهر المياه والخضرة باهتة
                    nr = newLuma + ((r - luma) * CHROMA).toInt()
                    ng = newLuma + ((g - luma) * CHROMA).toInt()
                    nb = newLuma + ((b - luma) * CHROMA).toInt()
                }
                val pr = nr.coerceIn(0, 255)
                val pg = ng.coerceIn(0, 255)
                val pb = nb.coerceIn(0, 255)
                pixels[i] = (alpha shl 24) or (pr shl 16) or (pg shl 8) or pb
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

        /** معامل تقوية اللون (chroma) لتعويض بهتان بلاطات Mapnik */
        private const val CHROMA = 1.4f

        /** حدّ يُعتبر ما دونه رمادياً (أرض/حدود) فلا نُقوّي لونه */
        private const val LOW_CHROMA_SPREAD = 24

        /** صبغة ليلية باردة للألوان الرمادية */
        private const val COOL_R = 4
        private const val COOL_B = 9

        /**
         * نقاط تحكم جدول الإضاءة (إضاءة قديمة ← إضاءة جديدة):
         * 0–100 نصوص وأسماء داكنة ← فاتحة، 150–246 أرض ومزارع وحدود شوارع ← داكنة،
         * 248–255 شوارع فاتحة (الأفتح في البلاطة) ← فاتحة، فتبقى شوارع فوق أرض داكنة.
         */
        private val CONTROL = intArrayOf(
            0, 240,
            50, 236,
            100, 205,
            150, 130,
            175, 100,
            200, 92,
            230, 66,
            246, 62,
            248, 172,
            255, 182
        )

        /** جدول 256 مدخلاً مبنية من نقاط التحكم بالاستيفاء الخطي */
        private val LUT: IntArray = IntArray(256).also { table ->
            val pairs = CONTROL.size / 2
            for (i in 0 until 256) {
                var p = 0
                while (p < pairs - 2 && CONTROL[(p + 1) * 2] < i) p++
                val x0 = CONTROL[p * 2]
                val y0 = CONTROL[p * 2 + 1]
                val x1 = CONTROL[(p + 1) * 2]
                val y1 = CONTROL[(p + 1) * 2 + 1]
                table[i] = if (x1 <= x0) y1 else y0 + (y1 - y0) * (i - x0) / (x1 - x0)
            }
        }
    }
}
