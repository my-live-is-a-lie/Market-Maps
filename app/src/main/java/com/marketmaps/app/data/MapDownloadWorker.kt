package com.marketmaps.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * تحميل خريطة في الخلفية مع إشعار تقدم.
 * يُمرَّر regionId عبر INPUT_REGION_ID.
 */
class MapDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val regionId = inputData.getString(KEY_REGION_ID) ?: return@withContext Result.failure()
        val region = MapCatalog.findById(regionId) ?: return@withContext Result.failure()

        setForeground(createForegroundInfo(0, region.nameAr))
        MapDownloader.resetFlags()

        // setProgress/setForeground معلّقتان — لا تُستدعيان من lambda عادية
        var lastNotifyPercent = -1
        val result = MapDownloader.download(applicationContext, region) { progress ->
            val p = progress.percent
            // تخزين آخر نسبة؛ التحديث المعلّق يتم عبر حلقة منفصلة غير ممكنة هنا
            // نحدّث الإشعار فقط كل 5٪ عبر runBlocking قصير لتجنب آلاف الاستدعاءات
            if (p >= lastNotifyPercent + 5 || p >= 100) {
                lastNotifyPercent = p
                try {
                    kotlinx.coroutines.runBlocking {
                        setProgress(
                            workDataOf(
                                KEY_PROGRESS to p,
                                KEY_DOWNLOADED to progress.downloadedBytes,
                                KEY_TOTAL to progress.totalBytes
                            )
                        )
                        setForeground(createForegroundInfo(p, region.nameAr))
                    }
                } catch (_: Exception) {
                }
            }
        }

        if (result.isSuccess) {
            // حفظ كخريطة نشطة للأوفلاين
            try {
                AppPreferences(applicationContext).setMapFileName(region.fileName)
            } catch (_: Exception) {
            }
            Result.success(workDataOf(KEY_FILE to region.fileName))
        } else {
            Result.retry()
        }
    }

    private fun createForegroundInfo(percent: Int, nameAr: String): ForegroundInfo {
        val channelId = "map_download"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "تحميل الخرائط", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("تحميل خريطة $nameAr")
            .setContentText("$percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_REGION_ID = "region_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_FILE = "file"
        private const val NOTIFICATION_ID = 4401
    }
}
