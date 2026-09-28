package com.marketmaps.app.util

/**
 * صيغ الجمع العربية للأعداد في الواجهة.
 *
 * - 1: «نتيجة واحدة» / «محل واحد» (بدون رقم قبل الاسم)
 * - 2: دائماً «2» + المفرد (مثل: 2 نتيجة)
 * - 3–10: الجمع (5 نتائج)
 * - 11+: المفرد بعد العدد (11 نتيجة)
 */
object ArabicPlurals {

    /**
     * @param feminine true للكلمات المؤنثة (نتيجة، منطقة، صورة، مرة) → «واحدة»
     *                 false للمذكر (محل، متر) → «واحد»
     */
    fun format(
        count: Int,
        singular: String,
        plural: String,
        zero: String? = null,
        feminine: Boolean = true
    ): String {
        val n = count
        return when {
            n <= 0 -> zero ?: "0 $plural"
            n == 1 -> if (feminine) "$singular واحدة" else "$singular واحد"
            n == 2 -> "2 $singular"
            n in 3..10 -> "$n $plural"
            else -> "$n $singular"
        }
    }

    /** نتيجة واحدة / 2 نتيجة / 5 نتائج */
    fun results(count: Int, zero: String? = null): String =
        format(count, "نتيجة", "نتائج", zero, feminine = true)

    /** منطقة واحدة / 2 منطقة / 5 مناطق */
    fun regions(count: Int, zero: String? = null): String =
        format(count, "منطقة", "مناطق", zero, feminine = true)

    /** صورة واحدة / 2 صورة / 5 صور */
    fun photos(count: Int, zero: String? = null): String =
        format(count, "صورة", "صور", zero, feminine = true)

    /** محل واحد / 2 محل / 5 محلات */
    fun stores(count: Int, zero: String? = null): String =
        format(count, "محل", "محلات", zero, feminine = false)

    /** مرة واحدة / 2 مرة / 5 مرات */
    fun times(count: Int, zero: String? = null): String =
        format(count, "مرة", "مرات", zero, feminine = true)

    /** متر واحد / 2 متر / 5 أمتار */
    fun meters(count: Int, zero: String? = null): String =
        format(count, "متر", "أمتار", zero, feminine = false)

    /** كيلومتر واحد / 2 كيلومتر / 5 كيلومترات */
    fun kilometers(count: Int, zero: String? = null): String =
        format(count, "كيلومتر", "كيلومترات", zero, feminine = false)
}
