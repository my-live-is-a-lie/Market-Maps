package com.marketmaps.app.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * يحفظ آخر عطل في ملف محلي لعرضه عند فتح التطبيق.
 */
object CrashHandler {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrash(appContext, throwable)
            } catch (_: Exception) {
                // تجاهل فشل الحفظ
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
}
