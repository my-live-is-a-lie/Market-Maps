package com.marketmaps.app

import android.app.Application
import com.marketmaps.app.util.CrashHandler

/**
 * نقطة دخول التطبيق — تسجيل معالج الأعطال مرة واحدة فقط.
 */
class MarketMapsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
