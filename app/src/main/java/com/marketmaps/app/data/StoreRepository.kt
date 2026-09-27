package com.marketmaps.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
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
                // وقت السيرفر — لا يعتمد على ساعة الهاتف
                "createdAt" to FieldValue.serverTimestamp()
            )
            val documentRef = collection.add(data).await()
            Result.success(documentRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logW(TAG, "Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun getAllStores(): Result<List<Store>> {
        return try {
            val snapshot = collection.get().await()
            val stores = snapshot.documents.mapNotNull { doc ->
                try {
                    val created = doc.getTimestamp("createdAt")?.toDate()?.time
                        ?: doc.getLong("createdAt")
                        ?: 0L
                    Store(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        category = doc.getString("category") ?: "",
                        description = doc.getString("description") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        createdAt = created
                    )
                } catch (e: Exception) {
                    logW(TAG, "مستند غير صالح ${doc.id}", e)
                    null
                }
            }
            Result.success(stores)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logW(TAG, "Firestore", e)
            Result.failure(e)
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
            logW(TAG, "Firestore", e)
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
            logW(TAG, "Firestore", e)
            Result.failure(e)
        }
    }
}

private const val TAG = "StoreRepository"
