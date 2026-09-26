package com.marketmaps.app.data

import android.graphics.Color
import java.util.concurrent.ConcurrentHashMap

/**
 * المصدر الوحيد لربط نص التصنيف (مثل "محل > بقالة") بلون العلامة واسم أيقونتها.
 *
 * سابقاً كانت الكلمات المفتاحية مكررة في colorForCategory و iconNameForCategory
 * و guessIconName، وكل استدعاء ينشئ ~8 قوائم جديدة ويحلل الألوان بـ parseColor،
 * ويُستدعى ذلك لكل محل ولكل شريحة فلتر في كل إعادة رسم.
 *
 * الآن: مجموعات الكلمات والألوان ثوابت تُنشأ مرة واحدة، والنتيجة تُحفظ لكل نص
 * تصنيف (عدد التصنيفات محدود) فيصبح الاستدعاء المتكرر مجرد بحث في HashMap.
 * ترتيب القواعد مطابق للسابق تماماً حتى لا يتغير أي لون أو أيقونة.
 */
object CategoryStyles {

    data class Style(val color: Int, val iconName: String)

    // ── مجموعات الكلمات المفتاحية (تُعرّف مرة واحدة وتُشارك بين القواعد) ──
    private val HEALTH = listOf("صيدلية", "مستشفى", "عيادة", "مختبر", "أسنان")
    private val FOOD = listOf("مطعم", "مقهى", "كافي", "وجبات", "شعبي", "أغذية", "restaurant")
    private val GROCERY = listOf("بقالة", "عطارة", "سوبر")
    private val WORKSHOP = listOf("ورشة", "سمكرة", "نجارة", "حدادة", "ميكانيكا", "كهرباء", "سباكة")
    private val SCHOOL = listOf("مدرسة", "ابتدائية", "إعدادية", "ثانوية", "لغات")
    private val CLOTHES = listOf("ملابس", "أحذية")
    private val HOME_AND_INDUSTRY = listOf("أجهزة", "كهربائية", "منزلية", "مواد بناء", "مصنع", "بلاستيك")

    // ── الألوان ──
    private val COLOR_HEALTH: Int = Color.parseColor("#E53935")
    private val COLOR_FOOD: Int = Color.parseColor("#FB8C00")
    private val COLOR_GROCERY: Int = Color.parseColor("#43A047")
    private val COLOR_WORKSHOP: Int = Color.parseColor("#8E24AA")
    private val COLOR_SCHOOL: Int = Color.parseColor("#1E88E5")
    private val COLOR_CLOTHES: Int = Color.parseColor("#EC407A")
    private val COLOR_HOME_AND_INDUSTRY: Int = Color.parseColor("#546E7A")
    private val COLOR_STORE: Int = Color.parseColor("#00897B")
    private val COLOR_DEFAULT: Int = Color.parseColor("#3949AB")

    private class Rule<T>(val keywords: List<String>, val value: T)

    /** أول قاعدة تطابق تفوز — نفس ترتيب colorForCategory القديم */
    private val colorRules: List<Rule<Int>> = listOf(
        Rule(HEALTH, COLOR_HEALTH),
        Rule(FOOD, COLOR_FOOD),
        Rule(GROCERY, COLOR_GROCERY),
        Rule(WORKSHOP, COLOR_WORKSHOP),
        Rule(SCHOOL + "مكتبة", COLOR_SCHOOL),
        Rule(CLOTHES, COLOR_CLOTHES),
        Rule(HOME_AND_INDUSTRY, COLOR_HOME_AND_INDUSTRY),
        Rule(listOf("محل", "store"), COLOR_STORE)
    )

    /** أول قاعدة تطابق تفوز — نفس ترتيب iconNameForCategory القديم */
    private val iconRules: List<Rule<String>> = listOf(
        Rule(listOf("مستشفى"), "hospital"),
        Rule(listOf("عيادة", "أسنان"), "clinic"),
        Rule(listOf("مختبر"), "lab"),
        Rule(WORKSHOP, "workshop"),
        Rule(listOf("مصنع", "بلاستيك"), "factory"),
        Rule(SCHOOL, "school"),
        Rule(listOf("مطعم", "وجبات", "شعبي", "أغذية"), "restaurant"),
        Rule(listOf("مقهى", "كافي"), "coffee_shop"),
        Rule(
            GROCERY + CLOTHES + listOf("محل", "صيدلية", "مكتبة", "أدوات", "أجهزة", "مواد"),
            "store"
        )
    )

    private const val MAX_CACHE = 512
    private val cache = ConcurrentHashMap<String, Style>()

    fun styleFor(category: String): Style {
        cache[category]?.let { return it }
        val c = category.lowercase()
        val style = Style(
            color = colorRules.firstOrNull { rule -> rule.keywords.any { it in c } }?.value
                ?: COLOR_DEFAULT,
            iconName = iconRules.firstOrNull { rule -> rule.keywords.any { it in c } }?.value
                ?: "other"
        )
        // التصنيفات محدودة؛ الحد يحمي فقط من نصوص حرة كثيرة جداً
        if (cache.size >= MAX_CACHE) cache.clear()
        cache[category] = style
        return style
    }

    fun colorFor(category: String): Int = styleFor(category).color

    fun iconNameFor(category: String): String = styleFor(category).iconName
}
