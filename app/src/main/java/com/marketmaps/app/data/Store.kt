package com.marketmaps.app.data

/**
 * نموذج بيانات المحل الذي يُحفظ في Firestore.
 */
data class Store(
    val id: String = "",
    val name: String = "",
    val category: String = "",          // مثال: "محل بقالة" أو "مدرسة ثانوية بنين"
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)
