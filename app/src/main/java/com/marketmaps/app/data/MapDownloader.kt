package com.marketmaps.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * تحميل ملفات خرائط Mapsforge مع إيقاف مؤقت واستئناف وإلغاء.
 */
object MapDownloader {

    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val percent: Int
    ) {
        val downloadedMb: Double get() = downloadedBytes / (1024.0 * 1024.0)
        val totalMb: Double get() = if (totalBytes > 0) totalBytes / (1024.0 * 1024.0) else 0.0
        fun formatLine(): String {
            val d = "%.1f".format(downloadedMb)
            val t = if (totalBytes > 0) "%.1f".format(totalMb) else "?"
            val sp = if (speedBytesPerSec > 1024 * 1024)
                "%.1f ميجا/ث".format(speedBytesPerSec / (1024.0 * 1024.0))
            else
                "%.0f كيلو/ث".format(speedBytesPerSec / 1024.0)
            return "تم تحميل $d من $t ميجا — $sp"
        }
    }

    data class DownloadedMap(
        val regionId: String,
        val fileName: String,
        val nameAr: String,
        val continentAr: String,
        val sizeBytes: Long,
        val file: File
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .build()

    @Volatile
    private var pauseFlag = AtomicBoolean(false)

    @Volatile
    private var cancelFlag = AtomicBoolean(false)

    fun mapsDir(context: Context): File {
        return File(context.filesDir, "maps").also { if (!it.exists()) it.mkdirs() }
    }

    fun mapFile(context: Context, fileName: String): File =
        File(mapsDir(context), fileName)

    fun tempFile(context: Context, fileName: String): File =
        File(mapsDir(context), "$fileName.tmp")

    fun isDownloaded(context: Context, fileName: String): Boolean {
        val f = mapFile(context, fileName)
        return f.exists() && f.length() > 1_000_000
    }

    /** توافق مع الكود القديم */
    fun egyptMapFile(context: Context): File = mapFile(context, "egypt.map")

    fun isEgyptMapDownloaded(context: Context): Boolean =
        isDownloaded(context, "egypt.map")

    fun listDownloaded(context: Context): List<DownloadedMap> {
        val dir = mapsDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".map") && it.length() > 1_000_000 }
            ?.mapNotNull { file ->
                val region = MapCatalog.regions.find { it.fileName == file.name }
                DownloadedMap(
                    regionId = region?.id ?: file.nameWithoutExtension,
                    fileName = file.name,
                    nameAr = region?.nameAr ?: file.nameWithoutExtension,
                    continentAr = region?.continentAr ?: "",
                    sizeBytes = file.length(),
                    file = file
                )
            }
            ?.sortedBy { it.nameAr }
            ?: emptyList()
    }

    fun pause() {
        pauseFlag.set(true)
    }

    fun resume() {
        pauseFlag.set(false)
    }

    fun cancel() {
        cancelFlag.set(true)
        pauseFlag.set(false)
    }

    fun resetFlags() {
        pauseFlag.set(false)
        cancelFlag.set(false)
    }

    /**
     * تحميل أو استئناف. يدعم Range للاستئناف من ملف .tmp.
     */
    suspend fun download(
        context: Context,
        region: MapCatalog.MapRegion,
        onProgress: (Progress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        resetFlags()
        try {
            val dest = mapFile(context, region.fileName)
            val temp = tempFile(context, region.fileName)
            val existing = if (temp.exists()) temp.length() else 0L

            val requestBuilder = Request.Builder().url(region.url)
            if (existing > 0) {
                requestBuilder.header("Range", "bytes=$existing-")
            }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    return@withContext Result.failure(Exception("فشل التحميل: ${response.code}"))
                }
                val body = response.body
                    ?: return@withContext Result.failure(Exception("استجابة فارغة"))

                val contentLength = body.contentLength()
                val total = when {
                    response.code == 206 && contentLength > 0 -> existing + contentLength
                    contentLength > 0 -> contentLength
                    else -> -1L
                }

                val append = response.code == 206 && existing > 0
                body.byteStream().use { input ->
                    FileOutputStream(temp, append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = existing
                        var lastProgress = -1
                        var windowStart = System.currentTimeMillis()
                        var windowBytes = 0L
                        var speed = 0L

                        while (true) {
                            coroutineContext.ensureActive()

                            while (pauseFlag.get() && !cancelFlag.get()) {
                                Thread.sleep(200)
                                coroutineContext.ensureActive()
                            }
                            if (cancelFlag.get()) {
                                return@withContext Result.failure(Exception("تم إلغاء التحميل"))
                            }

                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            windowBytes += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - windowStart
                            if (elapsed >= 500) {
                                speed = (windowBytes * 1000L) / elapsed.coerceAtLeast(1)
                                windowStart = now
                                windowBytes = 0L
                            }

                            val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
                            if (percent != lastProgress || elapsed >= 500) {
                                lastProgress = percent
                                onProgress(Progress(downloaded, total, speed, percent))
                            }
                        }
                    }
                }

                if (cancelFlag.get()) {
                    return@withContext Result.failure(Exception("تم إلغاء التحميل"))
                }

                if (dest.exists()) dest.delete()
                if (!temp.renameTo(dest)) {
                    temp.copyTo(dest, overwrite = true)
                    temp.delete()
                }
                onProgress(Progress(dest.length(), dest.length(), 0, 100))
                Result.success(dest)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            resetFlags()
        }
    }

    /** توافق قديم */
    suspend fun downloadEgyptMap(
        context: Context,
        onProgress: (Int) -> Unit
    ): Result<File> {
        val region = MapCatalog.findById("egypt")
            ?: return Result.failure(Exception("منطقة مصر غير موجودة في الكتالوج"))
        return download(context, region) { p -> onProgress(p.percent) }
    }

    fun deleteMap(context: Context, fileName: String): Boolean {
        tempFile(context, fileName).delete()
        return mapFile(context, fileName).delete()
    }

    fun deleteEgyptMap(context: Context) {
        deleteMap(context, "egypt.map")
    }

    fun deleteMaps(context: Context, fileNames: List<String>): Long {
        var freed = 0L
        fileNames.forEach { name ->
            val f = mapFile(context, name)
            if (f.exists()) {
                freed += f.length()
                f.delete()
            }
            tempFile(context, name).delete()
        }
        return freed
    }

    fun pendingTempBytes(context: Context, fileName: String): Long {
        val t = tempFile(context, fileName)
        return if (t.exists()) t.length() else 0L
    }
}
