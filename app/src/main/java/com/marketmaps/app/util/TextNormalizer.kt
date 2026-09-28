package com.marketmaps.app.util

/**
 * تطبيع النص العربي + توسيع المفرد/الجمع لتسهيل البحث.
 *
 * أمثلة:
 * - "بطاريات" تطابق محل مكتوب فيه "بطارية"
 * - "بطارية ريموت" تطابق "بطاريات ريموت"
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

    /**
     * يولّد أشكال بديلة للكلمة (مفرد ↔ جمع شائع في العربية).
     */
    fun expandToken(raw: String): Set<String> {
        val s = normalize(raw)
        if (s.length < 2) return if (s.isEmpty()) emptySet() else setOf(s)

        val out = linkedSetOf(s)

        // جمع مؤنث: ...ات ← جذر / جذر+ه
        if (s.endsWith("يات") && s.length > 4) {
            val stem = s.removeSuffix("يات")
            out += stem
            out += stem + "يه"
            out += stem + "ي"
        } else if (s.endsWith("ات") && s.length > 3) {
            val stem = s.removeSuffix("ات")
            out += stem
            out += stem + "ه"
        }

        // جمع مذكر سالم: ...ون / ...ين
        if (s.endsWith("ون") && s.length > 3) {
            out += s.removeSuffix("ون")
        }
        if (s.endsWith("ين") && s.length > 3) {
            out += s.removeSuffix("ين")
        }

        // مثنى: ...ان
        if (s.endsWith("ان") && s.length > 3) {
            out += s.removeSuffix("ان")
        }

        // من المفرد إلى الجمع التقريبي
        if (s.endsWith("ه") && s.length > 2) {
            val stem = s.removeSuffix("ه")
            out += stem + "ات"
            out += stem
        }
        if (s.endsWith("ي") && s.length > 2 && !s.endsWith("يي")) {
            out += s + "ات"
            out += s.removeSuffix("ي") + "يات"
        }

        // كلمات قصيرة شائعة جداً في البحث المحلي (بدون قائمة منتجات كاملة)
        when (s) {
            "بطاريه", "بطارياه" -> {
                out += "بطاريه"; out += "بطاريات"
            }
            "بطاريات" -> {
                out += "بطاريه"
            }
        }

        return out.filter { it.length >= 2 }.toSet()
    }

    /**
     * هل النص (اسم+تصنيف+وصف مطبّع) يطابق عبارة البحث بعد التطبيع وتوسيع المفرد/الجمع؟
     * كل كلمة في البحث يجب أن تطابق جزءاً من النص.
     */
    fun matchesSearch(normalizedHaystack: String, normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return true
        if (normalizedHaystack.contains(normalizedQuery)) return true

        val queryTokens = normalizedQuery
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }

        if (queryTokens.isEmpty()) return normalizedHaystack.contains(normalizedQuery)

        // كلمات النص المفصولة بمسافات أو فاصل الحقول
        val hayTokens = normalizedHaystack
            .split(Regex("[\\s|]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return queryTokens.all { qToken ->
            val qVariants = expandToken(qToken)
            // تطابق مباشر على النص الكامل
            if (qVariants.any { normalizedHaystack.contains(it) }) return@all true
            // تطابق كلمة بكلمة مع التوسيع
            hayTokens.any { hToken ->
                val hVariants = expandToken(hToken)
                qVariants.any { qv ->
                    hVariants.any { hv ->
                        hv == qv || hv.contains(qv) || qv.contains(hv)
                    }
                }
            }
        }
    }
}
