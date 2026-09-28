package com.marketmaps.app.ui.map

import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.queue.Job
import org.mapsforge.map.model.common.Observer
import com.marketmaps.app.util.logW

/**
 * غلاف كاش يلوّن بلاطات OSM المباشرة (بلاطات Mapnik الجاهزة) في الوضع الليلي
 * بنفس لوحة خرائط جوجل الليلية (نفس ألوان الأرض والمياه والخضرة والطرق والتسميات).
 *
 * لماذا على الكاش وليس على عرض الخريطة؟
 * بلاطات Mapnik صور جاهزة لا يمكن إعادة تلوينها من المصدر، والحل الشائع هو فلتر
 * على عرض الخريطة كاملاً — لكن ذلك يُفسد ألوان علامات التطبيق (الأبيض داخل
 * الشعارات يصبح أسود). هنا نُلوّن البلاطات مرة واحدة عند تحميلها ودخولها للكاش.
 *
 * كيف نُلوّن؟ (مهم)
 * بلاطات Mapnik فاتحة: الأرض بيج، الشوارع بيضاء، حدود الشوارع رمادي، النصوص داكنة.
 * نستخدم:
 *   1) جدول إضاءة (LUT) للعناصر الرمادية يقلب الترتيب ليطابق جوجل:
 *      نصوص داكنة ← بيضاء #FFFFFF، حدود ← #16202C، أرض ← #1B2531، شوارع ← #3F5261.
 *   2) للعناصر الملوّنة (مياه/خضرة/طرق ملوّنة): نحفظ اللون ونقوّيه، مع هدف إضاءة
 *      مختلف لكل عائلة لونية ليطابق لون جوجل (مياه كحلية #11304C، خضرة #1D4239،
 *      طرق كبيرة #556C7A).
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

    /** بلاطة جديدة بلوحة جوجل الليلية؛ تُرجع null إن تعذّر التحويل (فتُستخدم الأصلية) */
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

                val nr: Int
                val ng: Int
                val nb: Int
                if (spread < LOW_CHROMA_SPREAD) {
                    // رمادي/أرض/حدود/شوارع/نصوص: جدول الإضاءة الليلي، مع إلغاء
                    // الصبغة (أرض Mapnik البيج تصبح رمادية باردة مثل لوحة جوجل)
                    // وميل أزرق بسيط (#242F3E و #46505F أزرقان في جوجل)
                    val target = LUT[luma]
                    nr = target - COOL_R
                    ng = target
                    nb = target + COOL_B
                } else {
                    // ملوّن: نُعيّن لون تطبيق جوجل الرسمي مباشرة (كما في نمط الليل)
                    // مع الحفاظ على تفاوت الإضاءة داخل العنصر نفسه (غابة أغمق من مرج)
                    val bluish = b - r > BLUE_BIAS
                    val greenish = !bluish && g - r > GREEN_BIAS
                    val factor = (luma / 190f).coerceIn(0.75f, 1.3f)
                    if (bluish) {
                        // مياه كحلية #11304C
                        nr = (17 * factor).toInt()
                        ng = (48 * factor).toInt()
                        nb = (76 * factor).toInt()
                    } else if (greenish) {
                        // خضرة مزرقّة #1D4239
                        nr = (29 * factor).toInt()
                        ng = (66 * factor).toInt()
                        nb = (57 * factor).toInt()
                    } else {
                        // طرق كبيرة مائلة إلى الأزرق #556C7A
                        val warm = (luma / 200f).coerceIn(0.85f, 1.1f)
                        nr = (85 * warm).toInt()
                        ng = (108 * warm).toInt()
                        nb = (122 * warm).toInt()
                    }
                }

                pixels[i] = (alpha shl 24) or
                    (nr.coerceIn(0, 255) shl 16) or
                    (ng.coerceIn(0, 255) shl 8) or
                    nb.coerceIn(0, 255)
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

        /** تمييز الأزرق (مياه ب − أحمر) والأخضر (أخضر − أحمر) عن الألوان الدافئة */
        private const val BLUE_BIAS = 12
        private const val GREEN_BIAS = 8

        /** أهداف الإضاءة لمطابقة لوحة جوجل الليلية */
        private const val BLUE_TARGET = 43    // مياه #17639B
        private const val GREEN_TARGET = 57  // خضرة #2E7D4F
        private const val WARM_TARGET = 90   // طرق كبيرة #7A6A52

        /** ميل أزرق بارد للعناصر الرمادية ليطابق لون الأرض/الطرق في جوجل */
        private const val COOL_R = -6
        private const val COOL_B = 14

        /** تقوية اللون لتعويض بهتان بلاطات Mapnik */
        private const val CHROMA_BLUE = 1.9f
        private const val CHROMA_GREEN = 1.6f
        private const val CHROMA_WARM = 1.3f

        /**
         * نقاط تحكم جدول الإضاءة (إضاءة Mapnik ← إضاءة جوجل الليلية):
         * 0–110 نصوص داكنة ← فاتحة #E8EAED، ثم 150 ← 105 انتقالاً،
         * 200 حدود الشوارع ← #232C38 (43)، 242 الأرض ← #242F3E (46)،
         * 255 الشوارع البيضاء ← #46505F (79) — فتبقى الشوارع أعلى إضاءة من الأرض
         * وحدودها أغمق منها، تماماً كترتيب خرائط جوجل الليلية.
         */
        private val CONTROL = intArrayOf(
            0, 250,
            60, 244,
            110, 205,
            150, 120,
            175, 55,
            200, 33,
            225, 34,
            242, 36,
            250, 58,
            255, 79
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
