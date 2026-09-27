package com.marketmaps.app.util

/**
 * تطبيع النص العربي لتسهيل البحث (أ/إ/آ ← ا، ة ← ه، …)
 *
 * تمريرة واحدة على الحروف بدل 9 استدعاءات replace (كل منها ينسخ النص كاملاً)
 * + Regex جديد في كل استدعاء.
 */
object TextNormalizer {
    fun normalize(s: String): String {
        val src = s.trim()
        val sb = StringBuilder(src.length)
        for (ch in src) {
            when (ch) {
                'أ', 'إ', 'آ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى', 'ئ' -> sb.append('ي')
                'ؤ' -> sb.append('و')
                '\u0640' -> Unit // تطويل
                in '\u064B'..'\u065F' -> Unit // تشكيل
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString()
    }
}
