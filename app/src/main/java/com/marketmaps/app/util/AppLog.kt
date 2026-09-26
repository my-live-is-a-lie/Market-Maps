package com.marketmaps.app.util

import android.util.Log

/**
 * تسجيل موحّد بدل ابتلاع الاستثناءات بصمت.
 *
 * كان في المشروع أكثر من 17 موضع `catch (_: Exception) {}` بدون أي سطر Log، فعندما
 * لا تظهر علامة أو تفشل خريطة لا يبقى أي أثر للسبب. الآن كل موضع يسجل تحذيراً
 * يظهر في Logcat (ويمكن لاحقاً توجيهه إلى Crashlytics من هنا فقط).
 */
// بادئة قصيرة: أندرويد < 8 يرفض الوسوم الأطول من 23 حرفاً في isLoggable
private const val PREFIX = "MM/"

fun logW(tag: String, message: String, error: Throwable? = null) {
    Log.w(PREFIX + tag, message, error)
}

fun logE(tag: String, message: String, error: Throwable? = null) {
    Log.e(PREFIX + tag, message, error)
}

/**
 * مثل runCatching لكن يسجل الفشل. للاستخدام في العمليات "الاختيارية" التي
 * لا يجب أن توقف التطبيق لكن نريد معرفة سبب فشلها.
 */
inline fun <T> logged(tag: String, message: String, block: () -> T): T? =
    try {
        block()
    } catch (e: Exception) {
        logW(tag, message, e)
        null
    }
