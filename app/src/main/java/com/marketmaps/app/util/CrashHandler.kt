package com.marketmaps.app.util

import android.content.Context
import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * يحفظ آخر عطل محلياً (لعرضه في التطبيق) ويرسله إلى Firebase Crashlytics.
 */
object CrashHandler {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        // مفاتيح تساعد في تصنيف التقارير على Console
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
            crashlytics.setCustomKey("android_api", Build.VERSION.SDK_INT)
        } catch (e: Exception) {
            logW("CrashHandler", "تعذر تهيئة مفاتيح Crashlytics", e)
        }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrash(appContext, throwable)
            } catch (e: Exception) {
                logE("CrashHandler", "تعذر حفظ تقرير الانهيار", e)
            }
            try {
                // يُرسل عند إعادة فتح التطبيق (أو فوراً إن وُجد اتصال)
                FirebaseCrashlytics.getInstance().recordException(throwable)
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun saveCrash(context: Context, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val text = buildString {
            appendLine("=== تقرير عطل Market Maps ===")
            appendLine("الوقت: $time")
            appendLine("الجهاز: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("أندرويد: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("الرسالة: ${throwable.message}")
            appendLine()
            appendLine("--- التفاصيل ---")
            appendLine(sw.toString())
        }

        File(context.filesDir, FILE_NAME).writeText(text)
    }

    fun readLastCrash(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val text = file.readText()
        return text.ifBlank { null }
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    /** خطأ غير قاتل — يظهر في Crashlytics دون إغلاق التطبيق */
    fun recordNonFatal(throwable: Throwable, message: String? = null) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            if (!message.isNullOrBlank()) {
                crashlytics.log(message)
            }
            crashlytics.recordException(throwable)
        } catch (_: Exception) {
        }
    }
}
