package com.marketmaps.app.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * تبديل أيقونة الشاشة الرئيسية.
 *
 * نظام أندرويد لا يسمح بتغيير أيقونة المشغّل مباشرة، بل عبر مكوّنات «أيقونة بديلة»
 * (activity-alias) تُعلَن مسبقاً في البيان — يولّدها البناء لكل تركيبة شعار × لون —
 * ثم يفعّل التطبيق واحداً منها ويُعطّل الباقي حسب اختيار المستخدم.
 *
 * قواعد السلامة (بعد عطل سابق بسببها):
 * 1) لا تُعطَّل أي أيقونة قبل التأكد من أن الأيقونة المطلوبة مفعّلة فعلياً — وإلا
 *    ينتهي التطبيق بصفر أيقونات فيختفي من الشاشة الرئيسية.
 * 2) إن لم توجد الأيقونة المطلوبة أو فشل تفعيلها: لا نغيّر أي شيء إطلاقاً.
 * 3) بعد كل عملية نتأكد أن أيقونة واحدة على الأقل مفعّلة، وإن لم تكن نفعّل الافتراضية.
 */
object LauncherIconSwitcher {

    private const val ALIAS_MARKER = "LauncherIcon_"

    private const val STATE_DEFAULT = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    private const val STATE_ENABLED = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    private const val STATE_DISABLED = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /** اسم مكوّن الأيقونة (شعار + لون) كما وُلّد في البيان */
    fun aliasName(context: Context, logoKey: String, backgroundKey: String): String =
        "${context.packageName}.$ALIAS_MARKER${logoKey}_$backgroundKey"

    /** كل الأيقونات البديلة مع حالة التفعيل الفعلية (تأخذ enabled في البيان بالحسبان) */
    private fun aliases(context: Context): List<Pair<String, Boolean>> {
        val manager = context.packageManager
        val activities = try {
            manager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES).activities
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        return activities.mapNotNull { activity ->
            val name = activity.name ?: return@mapNotNull null
            if (!name.contains(ALIAS_MARKER)) return@mapNotNull null
            val component = ComponentName(context.packageName, name)
            val setting = try {
                manager.getComponentEnabledSetting(component)
            } catch (_: Exception) {
                STATE_DISABLED
            }
            // DEFAULT = حسب البيان (المولَّد: أول أيقونة مفعّلة والباقي معطّل)
            val effective = when (setting) {
                STATE_ENABLED -> true
                STATE_DISABLED -> false
                else -> activity.enabled
            }
            name to effective
        }
    }

    /** أسماء الأيقونات المفعّلة حالياً */
    fun enabledAliases(context: Context): List<String> =
        aliases(context).filter { it.second }.map { it.first }

    /** تعيين حالة مكوّن، مع إعادة قراءة للتأكد */
    private fun setState(context: Context, componentName: String, enabled: Boolean): Boolean {
        val manager = context.packageManager
        val component = ComponentName(context.packageName, componentName)
        return try {
            manager.setComponentEnabledSetting(
                component,
                if (enabled) STATE_ENABLED else STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            manager.getComponentEnabledSetting(component) ==
                if (enabled) STATE_ENABLED else STATE_DISABLED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * يفعّل أيقونة (شعار + لون) ويُعطّل الباقي — بالترتيب الآمن:
     * تفعيل المطلوبة والتحقق منها أولاً، ثم تعطيل الباقي، ثم التأكد من وجود أيقونة.
     */
    fun apply(context: Context, logoKey: String, backgroundKey: String): Boolean {
        if (logoKey.isBlank() || backgroundKey.isBlank()) return false
        val all = aliases(context)
        if (all.isEmpty()) return false

        val target = aliasName(context, logoKey, backgroundKey)
        // 1) الأيقونة المطلوبة يجب أن تكون موجودة، وإلا لا نغيّر شيئاً
        if (all.none { it.first == target }) return false
        // 2) تفعيلها أولاً مع التحقق
        if (!setState(context, target, enabled = true)) return false
        // 3) الآن فقط نُعطّل الباقي
        all.filter { it.first != target }.forEach { (name, enabled) ->
            if (enabled) setState(context, name, enabled = false)
        }
        // 4) شبكة أمان: لا نترك التطبيق بلا أي أيقونة أبداً
        ensureAtLeastOneEnabled(context)
        return true
    }

    /** إن لم تكن أي أيقونة مفعّلة نفعّل الأولى (تُستخدم عند فتح التطبيق أيضاً) */
    fun ensureAtLeastOneEnabled(context: Context): String? {
        val all = aliases(context)
        all.firstOrNull { it.second }?.let { return it.first }
        val fallback = all.firstOrNull()?.first ?: return null
        return if (setState(context, fallback, enabled = true)) fallback else null
    }

    /**
     * يُستدعى عند فتح التطبيق: يضمن وجود أيقونة واحدة على الأقل، وإن كان المحفوظ
     * معطّلاً (بعد تغيير لم يكتمل) يُعيد تفعيله. يصلح حالة «اختفاء الأيقونة».
     */
    fun ensureValidIcon(context: Context, logoKey: String, backgroundValue: String): String? {
        val all = aliases(context)
        if (all.isEmpty()) return null
        val enabled = all.filter { it.second }
        if (enabled.size == 1) return enabled.first().first
        // لا شيء مفعّل أو أكثر من واحد: نضبطها على المطلوب (أو الأولى كاحتياط)
        val presetKeys = AppLogoDefaults.backgroundKeys
        val safeBackground = if (backgroundValue in presetKeys) backgroundValue else AppLogoDefaults.defaultBackground
        val target = aliasName(context, logoKey.ifBlank { AppLogoDefaults.defaultLogo }, safeBackground)
        if (all.any { it.first == target } && apply(context, logoKey.ifBlank { AppLogoDefaults.defaultLogo }, safeBackground)) {
            return target
        }
        return ensureAtLeastOneEnabled(context)
    }

    /** أقرب لون جاهز للّون المخصص (ترحيل الإعدادات القديمة) */
    fun nearestBackground(color: androidx.compose.ui.graphics.Color, backgrounds: List<AppLogoBackground>): AppLogoBackground? =
        backgrounds.minByOrNull { background ->
            val dr = (background.color.red - color.red).toDouble()
            val dg = (background.color.green - color.green).toDouble()
            val db = (background.color.blue - color.blue).toDouble()
            dr * dr + dg * dg + db * db
        }
}

/** قيم افتراضية تُستخدم عند الحاجة لاحتياط (مطابقة لما يولّده البناء) */
object AppLogoDefaults {
    val backgroundKeys = listOf("blue", "teal", "green", "orange", "red", "purple", "indigo", "dark")
    const val defaultBackground = "blue"
    const val defaultLogo = "market"
}
