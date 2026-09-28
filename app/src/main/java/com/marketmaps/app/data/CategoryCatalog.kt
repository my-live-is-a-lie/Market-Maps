package com.marketmaps.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.marketmaps.app.util.logE
import com.marketmaps.app.util.logW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * مصدر التصنيفات الحي:
 * 1) يبدأ بالقائمة المحلية الافتراضية
 * 2) يستمع لمستند Firestore: config/categories
 * 3) إن لم يوجد المستند يزرع القائمة الافتراضية مرة واحدة
 *
 * لتعديل التصنيفات لاحقاً: عدّل المستند من Firebase Console دون تحديث التطبيق.
 */
object CategoryCatalog {

    private const val TAG = "CategoryCatalog"
    private const val COLLECTION = "config"
    private const val DOCUMENT = "categories"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var started = false

    private val _categories = MutableStateFlow(CategoryData.defaultCategories)
    val categories: StateFlow<List<CategoryData.Category>> = _categories.asStateFlow()

    /** يبدأ الاستماع مرة واحدة من Application */
    fun start() {
        scope.launch {
            mutex.withLock {
                if (started) return@withLock
                started = true
            }
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection(COLLECTION).document(DOCUMENT)

            // زرع أولي إن لزم
            try {
                val existing = try {
                    docRef.get(Source.SERVER).await()
                } catch (_: Exception) {
                    docRef.get(Source.CACHE).await()
                }
                if (!existing.exists()) {
                    logW(TAG, "لا يوجد مستند تصنيفات — زرع القائمة الافتراضية")
                    docRef.set(toFirestoreMap(CategoryData.defaultCategories)).await()
                }
            } catch (e: Exception) {
                logE(TAG, "تعذر التحقق/زرع التصنيفات — نستمر بالقائمة المحلية", e)
            }

            docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logE(TAG, "خطأ مستمع التصنيفات", error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val parsed = parseFromFirestore(snapshot.data)
                if (parsed.isNotEmpty()) {
                    _categories.value = parsed
                    logW(TAG, "تحديث التصنيفات من السحابة: ${parsed.size} نوع")
                }
            }
        }
    }

    private fun toFirestoreMap(list: List<CategoryData.Category>): Map<String, Any> {
        return mapOf(
            "categories" to list.map { cat ->
                mapOf(
                    "name" to cat.name,
                    "subCategories" to cat.subCategories.map { sub ->
                        mapOf(
                            "name" to sub.name,
                            "thirdLevel" to sub.thirdLevel
                        )
                    }
                )
            }
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFromFirestore(data: Map<String, Any>?): List<CategoryData.Category> {
        if (data == null) return emptyList()
        val rawList = data["categories"] as? List<*> ?: return emptyList()
        return rawList.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"] as? String ?: return@mapNotNull null
            val subsRaw = map["subCategories"] as? List<*> ?: emptyList<Any>()
            val subs = subsRaw.mapNotNull { s ->
                val sm = s as? Map<*, *> ?: return@mapNotNull null
                val sn = sm["name"] as? String ?: return@mapNotNull null
                val third = (sm["thirdLevel"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                CategoryData.SubCategory(sn, third)
            }
            CategoryData.Category(name, subs)
        }
    }
}
