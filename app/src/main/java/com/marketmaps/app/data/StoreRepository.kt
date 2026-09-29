package com.marketmaps.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.marketmaps.app.util.logE
import com.marketmaps.app.util.logW
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * مستودع المحلات في Firestore.
 * المزامنة الحية عبر [observeStores] تُحدّث القائمة عند أي إضافة/تعديل/حذف من أي جهاز.
 */
class StoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("stores")

    fun observeStores(): Flow<List<Store>> = callbackFlow {
        var registration: ListenerRegistration? = null
        try {
            registration = collection.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    logE(TAG, "observeStores خطأ في المستمع", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val stores = snapshot.documents.mapNotNull { doc ->
                    try {
                        parseStore(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        logW(TAG, "مستند غير صالح ${doc.id}", e)
                        null
                    }
                }
                logW(
                    TAG,
                    "مزامنة: ${stores.size} محل (fromCache=${snapshot.metadata.isFromCache}, pending=${snapshot.metadata.hasPendingWrites()})"
                )
                trySend(stores)
            }
        } catch (e: Exception) {
            logE(TAG, "فشل بدء observeStores", e)
            close(e)
            return@callbackFlow
        }
        awaitClose { registration?.remove() }
    }

    /**
     * إضافة محل. إن وُجدت [photoUrls] تُحفظ مع المستند.
     * يُفضّل رفع الصور أولاً عبر [StorePhotoUploader] بعد الحصول على معرّف المستند.
     */
    suspend fun addStore(store: Store): Result<String> {
        return try {
            val docRef = collection.document()
            val data = storeToMap(store, includeCreated = true)
            docRef.set(data).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logW(TAG, "addStore", e)
            Result.failure(e)
        }
    }

    /** يحدّث روابط الصور فقط بعد اكتمال الرفع */
    suspend fun setPhotoUrls(storeId: String, photoUrls: List<String>): Result<Unit> {
        return try {
            if (storeId.isBlank()) return Result.failure(Exception("معرف المحل غير موجود"))
            collection.document(storeId).update("photoUrls", photoUrls).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logW(TAG, "setPhotoUrls", e)
            Result.failure(e)
        }
    }

    suspend fun getAllStores(): Result<List<Store>> {
        return try {
            val snapshot = try {
                collection.get(Source.SERVER).await()
            } catch (e: Exception) {
                logW(TAG, "السيرفر غير متاح — محاولة من الكاش", e)
                collection.get(Source.CACHE).await()
            }
            val stores = snapshot.documents.mapNotNull { doc ->
                try {
                    parseStore(doc.id, doc.data ?: emptyMap())
                } catch (e: Exception) {
                    logW(TAG, "مستند غير صالح ${doc.id}", e)
                    null
                }
            }
            Result.success(stores)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE(TAG, "getAllStores فشل", e)
            Result.failure(e)
        }
    }

    private fun storeToMap(store: Store, includeCreated: Boolean): HashMap<String, Any> {
        val data = hashMapOf<String, Any>(
            "name" to store.name,
            "category" to store.category,
            "description" to store.description,
            "latitude" to store.latitude,
            "longitude" to store.longitude,
            "photoUrls" to store.photoUrls
        )
        if (includeCreated) {
            data["createdAt"] = FieldValue.serverTimestamp()
        } else {
            data["updatedAt"] = FieldValue.serverTimestamp()
        }
        return data
    }

    private fun parseStore(id: String, data: Map<String, Any?>): Store? {
        if (data["isDeleted"] == true) return null
        val createdRaw = data["createdAt"]
        val created = when (createdRaw) {
            is com.google.firebase.Timestamp -> createdRaw.toDate().time
            is Long -> createdRaw
            is Double -> createdRaw.toLong()
            else -> 0L
        }
        val photos = when (val p = data["photoUrls"]) {
            is List<*> -> p.mapNotNull { it as? String }.filter { it.isNotBlank() }
            else -> emptyList()
        }
        return Store(
            id = id,
            name = data["name"] as? String ?: "",
            category = data["category"] as? String ?: "",
            description = data["description"] as? String ?: "",
            latitude = numberToDouble(data["latitude"]),
            longitude = numberToDouble(data["longitude"]),
            createdAt = created,
            photoUrls = photos
        )
    }

    private fun numberToDouble(value: Any?): Double {
        return when (value) {
            is Double -> value
            is Float -> value.toDouble()
            is Long -> value.toDouble()
            is Int -> value.toDouble()
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    suspend fun updateStore(store: Store): Result<Unit> {
        return try {
            if (store.id.isBlank()) return Result.failure(Exception("معرف المحل غير موجود"))
            collection.document(store.id).update(storeToMap(store, includeCreated = false) as Map<String, Any>).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logW(TAG, "updateStore", e)
            Result.failure(e)
        }
    }

    /** إخفاء المكان (حذف منطقي)؛ قواعد Firestore الحالية تمنع الحذف النهائي للمستندات. */
    suspend fun deleteStore(storeId: String): Result<Unit> {
        return try {
            if (storeId.isBlank()) return Result.failure(Exception("معرف المحل غير موجود"))
            collection.document(storeId).update("isDeleted", true).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logW(TAG, "deleteStore", e)
            Result.failure(e)
        }
    }
}

private const val TAG = "StoreRepository"
