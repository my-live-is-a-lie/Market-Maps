package com.marketmaps.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.marketmaps.app.util.logW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * رفع صور المحل إلى Firebase Storage بعد ضغطها لتقليل الحجم.
 */
class StorePhotoUploader(
    private val context: Context
) {
    private val storage = FirebaseStorage.getInstance()

    /**
     * يرفع قائمة صور ويرجع روابط التنزيل العامة.
     * يتجاهل الصور الفاشلة ويستمر في الباقي.
     */
    suspend fun upload(storeId: String, uris: List<Uri>, maxCount: Int = 3): List<String> {
        if (uris.isEmpty() || storeId.isBlank()) return emptyList()
        val limited = uris.take(maxCount)
        val urls = ArrayList<String>(limited.size)
        for (uri in limited) {
            try {
                val bytes = withContext(Dispatchers.IO) { compressJpeg(uri) } ?: continue
                val ref = storage.reference
                    .child("stores")
                    .child(storeId)
                    .child("${UUID.randomUUID()}.jpg")
                ref.putBytes(bytes).await()
                val url = ref.downloadUrl.await().toString()
                urls.add(url)
            } catch (e: Exception) {
                logW(TAG, "فشل رفع صورة", e)
            }
        }
        return urls
    }

    private fun compressJpeg(uri: Uri, maxSide: Int = 1280, quality: Int = 80): ByteArray? {
        val resolver = context.contentResolver
        // قراءة الأبعاد أولاً
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val w = bounds.outWidth
        val h = bounds.outHeight
        while (w / sample > maxSide * 2 || h / sample > maxSide * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val scaled = if (bmp.width > maxSide || bmp.height > maxSide) {
            val ratio = minOf(maxSide.toFloat() / bmp.width, maxSide.toFloat() / bmp.height)
            val nw = (bmp.width * ratio).toInt().coerceAtLeast(1)
            val nh = (bmp.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bmp, nw, nh, true).also {
                if (it !== bmp) bmp.recycle()
            }
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (!scaled.isRecycled) scaled.recycle()
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "StorePhotoUploader"
    }
}
