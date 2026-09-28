package com.marketmaps.app.ui.map

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
 * ملاحظة: الكاش يحفظ البلاطات الملوَّنة، لذلك [MapLayerHelper.applyOnline] يمسح
 * كاش الأونلاين عند تغيير الوضع الليلي ليعاد التحميل بالألوان الصحيحة.
 */
class NightTileCache(
    private val delegate: TileCache,
    private val night: Boolean
) : TileCache {

    private val paint: Paint by lazy {
        Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(nightColorMatrix())
        }
    }

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
        if (!night) {
            delegate.put(key, bitmap)
            return
        }
        delegate.put(key, recolor(bitmap) ?: bitmap)
    }

    /** بلاطة جديدة بنفس المقاس والألوان الليلية؛ تُرجع null إن تعذّر التحويل */
    private fun recolor(src: TileBitmap): TileBitmap? = try {
        val androidSrc = AndroidGraphicFactory.getBitmap(src)
        val size = androidSrc?.width ?: 0
        if (androidSrc == null || size <= 0 || androidSrc.height != size) {
            null
        } else {
            val out = AndroidGraphicFactory.INSTANCE.createTileBitmap(size, androidSrc.hasAlpha())
            val androidOut = AndroidGraphicFactory.getBitmap(out)
            if (androidOut == null) {
                null
            } else {
                Canvas(androidOut).drawBitmap(androidSrc, 0f, 0f, paint)
                out.setTimestamp(src.getTimestamp())
                out
            }
        }
    } catch (e: Exception) {
        logW(TAG, "تعذّر تلوين البلاطة للوضع الليلي", e)
        null
    }

    companion object {
        private const val TAG = "NightTileCache"

        /**
         * مصفوفة ألوان ليلية للبلاطات الفاتحة:
         * عكس الألوان ← تدوير الدرجة 180° (نفس أسلوب CSS filter) ← رفع التباين والسطوع.
         * بلا رفع التباين تصبح الخريطة سوداء تقريباً بشوارع رمادية باهتة (صعبة الرؤية).
         * الناتج: خلفية #1A1A1A، شوارع رمادية فاتحة، مبانٍ وبحيرات داكنة، وأسماء الشوارع
         * تصبح فاتحة (النص الداكن يتحوّل إلى أبيض).
         */
        private fun nightColorMatrix(): ColorMatrix {
            val invert = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            // hue-rotate(180°) وفق مواصفة CSS filter
            val hueRotate = ColorMatrix(
                floatArrayOf(
                    -0.574f, 1.430f, 0.144f, 0f, 0f,
                    0.426f, 0.430f, 0.144f, 0f, 0f,
                    0.426f, 1.430f, -0.856f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            // رفع التباين والسطوع: الناتج = 1.25 × اللون + 15
            val contrast = ColorMatrix(
                floatArrayOf(
                    1.25f, 0f, 0f, 0f, 15f,
                    0f, 1.25f, 0f, 0f, 15f,
                    0f, 0f, 1.25f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            invert.postConcat(hueRotate)
            invert.postConcat(contrast)
            return invert
        }
    }
}
