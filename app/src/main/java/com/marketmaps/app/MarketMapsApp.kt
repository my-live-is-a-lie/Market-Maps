package com.marketmaps.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.marketmaps.app.ui.map.MarkerIconHelper
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.marketmaps.app.util.CrashHandler
import com.marketmaps.app.data.CategoryCatalog

/**
 * نقطة دخول التطبيق — تسجيل معالج الأعطال مرة واحدة فقط.
 */
class MarketMapsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // تفعيل جمع التقارير (مفعّل افتراضياً؛ نؤكده صراحة)
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        } catch (_: Exception) {
        }
        CrashHandler.install(this)
        CategoryCatalog.start()
    }

    /**
     * عند خروج التطبيق للخلفية أو ضغط الذاكرة: تفريغ كاش أيقونات العلامات (حتى ~6MB)
     * وملفات SVG المحلَّلة، فيقل احتمال أن يغلق النظام التطبيق. تُعاد بنائها عند الحاجة.
     * (بلاطات الخريطة تُفرَّغ من MapScreen لأنه يملك الطبقات.)
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            MarkerIconHelper.clearCache()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        @Suppress("DEPRECATION")
        super.onLowMemory()
        MarkerIconHelper.clearCache()
    }
}
