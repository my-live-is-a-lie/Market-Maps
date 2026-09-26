package com.marketmaps.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * جدولة تحميل خريطة عبر WorkManager (يستمر حتى لو أُغلقت شاشة الإعدادات).
 */
object MapDownloadScheduler {

    private const val UNIQUE_PREFIX = "map_download_"

    fun enqueue(context: Context, regionId: String) {
        val data = workDataOf(MapDownloadWorker.KEY_REGION_ID to regionId)
        val request = OneTimeWorkRequestBuilder<MapDownloadWorker>()
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_PREFIX + regionId,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context, regionId: String) {
        MapDownloader.cancel()
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PREFIX + regionId)
    }
}
