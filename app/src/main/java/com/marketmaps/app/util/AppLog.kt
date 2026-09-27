package com.marketmaps.app.util

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * تسجيل موحّد. الأخطاء (logE) تُرسل أيضاً إلى Crashlytics كأعطال غير قاتلة.
 */
private const val PREFIX = "MM/"

fun logW(tag: String, message: String, error: Throwable? = null) {
    Log.w(PREFIX + tag, message, error)
}

fun logE(tag: String, message: String, error: Throwable? = null) {
    Log.e(PREFIX + tag, message, error)
    if (error != null) {
        CrashHandler.recordNonFatal(error, "$tag: $message")
    }
}

/**
 * مثل runCatching لكن يسجل الفشل. للاستخدام في العمليات "الاختيارية" التي
 * لا يجب أن توقف التطبيق لكن نريد معرفة سبب فشلها.
 */
inline fun <T> logged(tag: String, message: String, block: () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e // إلغاء الـ coroutine ليس خطأً — يجب أن يمر
    } catch (e: Exception) {
        logW(tag, message, e)
        null
    }
