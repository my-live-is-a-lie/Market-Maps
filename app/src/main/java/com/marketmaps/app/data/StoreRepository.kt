package com.marketmaps.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * مستودع للتعامل مع المحلات في Firestore.
 */
class StoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("stores")

    /**
     * حفظ محل جديد.
     */
    suspend fun addStore(store: Store): Result<String> {
        return try {
            val data = hashMapOf(
                "name" to store.name,
                "category" to store.category,
                "description" to store.description,
                "latitude" to store.latitude,
                "longitude" to store.longitude,
                "createdAt" to store.createdAt
            )
            val documentRef = collection.add(data).await()
            Result.success(documentRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * جلب كل المحلات.
     */
    suspend fun getAllStores(): Result<List<Store>> {
        return try {
            val snapshot = collection.get().await()
            val stores = snapshot.documents.mapNotNull { doc ->
                try {
                    Store(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        category = doc.getString("category") ?: "",
                        description = doc.getString("description") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                } catch (e: Exception) {
                    null
                }
            }
            Result.success(stores)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
