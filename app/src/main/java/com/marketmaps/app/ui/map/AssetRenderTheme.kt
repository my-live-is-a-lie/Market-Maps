package com.marketmaps.app.ui.map

import android.content.Context
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback
import org.mapsforge.map.rendertheme.XmlThemeResourceProvider
import java.io.IOException
import java.io.InputStream

/**
 * ثيم رسم يُقرأ من ملفات assets الخاصة بالتطبيق.
 *
 * مكتبة mapsforge المستخدمة (0.25.0) لا تتضمّن ثيماً ليلية — الثيم الليلي الرسمي
 * (DARK) أُضيف في 0.26، وثيماً المكتبة الداخلية تُقرأ من مسار ثابت داخل المكتبة.
 * لذلك نحمل ملف الثيم الليلي الرسمي dark.xml من assets ونمرّره إلى طبقة الرسم
 * الأوفلاين بدل MapsforgeThemes.DEFAULT في الوضع الليلي.
 *
 * ملاحظة: العناصر المستخدمة في الملف مدعومة كلها في 0.25 (rule/area/line/caption/
 * symbol/lineSymbol/pathText/stylemenu/cat/layer/name/overlay)، والسمات الجديدة
 * تُتجاهل مع تسجيل تحذير فقط.
 */
class AssetRenderTheme(
    private val context: Context,
    private val assetPath: String
) : XmlRenderTheme {

    override fun getMenuCallback(): XmlRenderThemeMenuCallback? = null

    override fun getRelativePathPrefix(): String = ASSET_PREFIX

    @Throws(IOException::class)
    override fun getRenderThemeAsStream(): InputStream = context.assets.open(assetPath)

    override fun getResourceProvider(): XmlThemeResourceProvider? = null

    override fun setMenuCallback(menuCallback: XmlRenderThemeMenuCallback?) = Unit

    override fun setResourceProvider(resourceProvider: XmlThemeResourceProvider?) = Unit

    companion object {
        /** مسار الثيم الليلي الرسمي داخل assets */
        const val DARK_THEME_ASSET = "mapsforge/dark.xml"

        /**
         * بادئة الموارد النسبية: نفس بادئة ثيمات المكتبة الداخلية تقريباً،
         * ومسارات الثيم الليلي تبدأ بـ jar: فتُقرأ من موارد المكتبة مباشرة.
         */
        private const val ASSET_PREFIX = "assets/mapsforge/"
    }
}
