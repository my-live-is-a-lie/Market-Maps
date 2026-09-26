package com.marketmaps.app.util

/**
 * تطبيع النص العربي لتسهيل البحث (أ/إ/آ ← ا، ة ← ه، …)
 */
object TextNormalizer {
    fun normalize(s: String): String {
        return s.trim()
            .lowercase()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace("ؤ", "و")
            .replace("ئ", "ي")
            .replace("\u0640", "") // تطويل
            .replace(Regex("[\u064B-\u065F]"), "") // تشكيل
    }
}
