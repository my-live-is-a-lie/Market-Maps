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
     * يُرجع الـ id الذي أنشأه Firestore.
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
}
