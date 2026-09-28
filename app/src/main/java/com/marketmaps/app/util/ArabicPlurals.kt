package com.marketmaps.app.util

/**
 * صيغ الجمع العربية للأعداد (مفرد / مثنى / جمع / 11 فأكثر).
 *
 * قواعد شائعة في الواجهات:
 * - 0: صيغة الجمع أو نص مخصص
 * - 1: مفرد
 * - 2: مثنى
 * - 3–10: جمع
 * - 11+: المفرد بعد العدد (حسب النحو الفصيح)
 *
 * استخدم [format] في أي مكان يظهر فيه عدد + اسم قابل للجمع.
 */
object ArabicPlurals {

    /**
     * @param count العدد
     * @param singular المفرد مثل: نتيجة
     * @param dual المثنى مثل: نتيجتان
     * @param plural الجمع مثل: نتائج
     * @param zero نص اختياري للصفر (مثل: معطل أو لا نتائج). إن كان null يُستخدم "0 {plural}"
     */
    fun format(
        count: Int,
        singular: String,
        dual: String,
        plural: String,
        zero: String? = null
    ): String {
        val n = count
        return when {
            n <= 0 -> zero ?: "0 $plural"
            n == 1 -> "1 $singular"
            n == 2 -> "2 $dual"
            n in 3..10 -> "$n $plural"
            else -> "$n $singular" // 11، 12، …
        }
    }

    /** نتيجة / نتيجتان / نتائج */
    fun results(count: Int, zero: String? = null): String =
        format(count, "نتيجة", "نتيجتان", "نتائج", zero)

    /** منطقة / منطقتان / مناطق */
    fun regions(count: Int, zero: String? = null): String =
        format(count, "منطقة", "منطقتان", "مناطق", zero)

    /** صورة / صورتان / صور */
    fun photos(count: Int, zero: String? = null): String =
        format(count, "صورة", "صورتان", "صور", zero)

    /** محل / محلّان / محلات */
    fun stores(count: Int, zero: String? = null): String =
        format(count, "محل", "محلّان", "محلات", zero)

    /** مرة / مرتان / مرات */
    fun times(count: Int, zero: String? = null): String =
        format(count, "مرة", "مرتان", "مرات", zero)

    /** متر / متران / أمتار */
    fun meters(count: Int, zero: String? = null): String =
        format(count, "متر", "متران", "أمتار", zero)

    /** كيلومتر / كيلومتران / كيلومترات */
    fun kilometers(count: Int, zero: String? = null): String =
        format(count, "كيلومتر", "كيلومتران", "كيلومترات", zero)
}
