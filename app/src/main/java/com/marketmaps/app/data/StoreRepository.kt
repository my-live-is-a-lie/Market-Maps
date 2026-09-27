package com.marketmaps.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import com.marketmaps.app.util.logE
import com.marketmaps.app.util.logW
import kotlin.coroutines.cancellation.CancellationException

/**
 * مستودع للتعامل مع المحلات في Firestore.
 */
class StoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("stores")

    suspend fun addStore(store: Store): Result<String> {
        return try {
            val data = hashMapOf<String, Any>(
                "name" to store.name,
                "category" to store.category,
                "description" to store.description,
                "latitude" to store.latitude,
                "longitude" to store.longitude,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val documentRef = collection.add(data).await()
            Result.success(documentRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logW(TAG, "addStore", e)
            Result.failure(e)
        }
    }

    suspend fun getAllStores(): Result<List<Store>> {
        return try {
            // نفضّل السيرفر حتى لا تُرجع ذاكرة فارغة بعد تثبيت جديد
            val snapshot = try {
                collection.get(Source.SERVER).await()
            } catch (e: Exception) {
                logW(TAG, "السيرفر غير متاح — محاولة من الكاش", e)
                collection.get(Source.CACHE).await()
            }
            logW(TAG, "عدد المستندات من Firestore: ${snapshot.size()} (fromCache=${snapshot.metadata.isFromCache})")
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

    private fun parseStore(id: String, data: Map<String, Any?>): Store {
        val createdRaw = data["createdAt"]
        val created = when (createdRaw) {
            is com.google.firebase.Timestamp -> createdRaw.toDate().time
            is Long -> createdRaw
            is Double -> createdRaw.toLong()
            else -> 0L
        }
        return Store(
            id = id,
            name = data["name"] as? String ?: "",
            category = data["category"] as? String ?: "",
            description = data["description"] as? String ?: "",
            latitude = numberToDouble(data["latitude"]),
            longitude = numberToDouble(data["longitude"]),
            createdAt = created
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
            val data = hashMapOf<String, Any>(
                "name" to store.name,
                "category" to store.category,
                "description" to store.description,
                "latitude" to store.latitude,
                "longitude" to store.longitude,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            collection.document(store.id).update(data).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logW(TAG, "updateStore", e)
            Result.failure(e)
        }
    }

    suspend fun deleteStore(storeId: String): Result<Unit> {
        return try {
            if (storeId.isBlank()) return Result.failure(Exception("معرف المحل غير موجود"))
            collection.document(storeId).delete().await()
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
