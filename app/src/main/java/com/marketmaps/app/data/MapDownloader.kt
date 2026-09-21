package com.marketmaps.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * تحميل ملف خريطة Mapsforge إلى مجلد التطبيق.
 */
object MapDownloader {

    const val EGYPT_MAP_URL = "https://download.mapsforge.org/maps/v5/africa/egypt.map"
    const val EGYPT_MAP_FILE = "egypt.map"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    fun mapsDir(context: Context): File {
        return File(context.filesDir, "maps").also { if (!it.exists()) it.mkdirs() }
    }

    fun egyptMapFile(context: Context): File {
        return File(mapsDir(context), EGYPT_MAP_FILE)
    }

    fun isEgyptMapDownloaded(context: Context): Boolean {
        val file = egyptMapFile(context)
        return file.exists() && file.length() > 1_000_000 // أكبر من 1 ميجا تقريباً
    }

    /**
     * @param onProgress نسبة من 0 إلى 100
     */
    suspend fun downloadEgyptMap(
        context: Context,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dest = egyptMapFile(context)
            val temp = File(dest.parentFile, "${EGYPT_MAP_FILE}.tmp")

            val request = Request.Builder().url(EGYPT_MAP_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("فشل التحميل: ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("استجابة فارغة"))
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastProgress = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val progress = ((downloaded * 100) / total).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    onProgress(progress)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (dest.exists()) dest.delete()
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
            onProgress(100)
            Result.success(dest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteEgyptMap(context: Context): Boolean {
        return egyptMapFile(context).delete()
    }
}
