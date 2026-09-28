package com.marketmaps.app.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.Color

/**
 * تبديل أيقونة الشاشة الرئيسية.
 *
 * نظام أندرويد لا يسمح بتغيير أيقونة المشغّل مباشرة، بل عبر مكوّنات «أيقونة بديلة»
 * (activity-alias) تُعلَن مسبقاً في البيان — يولّدها البناء لكل تركيبة شعار × لون —
 * ثم يفعّل التطبيق واحداً منها ويُعطّل الباقي حسب اختيار المستخدم.
 *
 * ملاحظات مهمة تعلمناها من أخطاء سابقة:
 * 1) الأيقونات المعطّلة **لا تظهر** في getPackageInfo إلا مع
 *    GET_DISABLED_COMPONENTS — بدونها لا نرى إلا الأيقونة المفعّلة حالياً،
 *    فيفشل التبديل لأنه لا يجد الأيقونة المطلوبة أصلاً (كان سبب عدم تبدّل الأيقونة).
 * 2) لا تُعطَّل أي أيقونة قبل التأكد من تفعيل المطلوبة، وإلا قد يبقى التطبيق
 *    بلا أيقونة فيختفي من الشاشة الرئيسية.
 */
object LauncherIconSwitcher {

    private const val ALIAS_MARKER = "LauncherIcon_"

    private const val STATE_DEFAULT = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    private const val STATE_ENABLED = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    private const val STATE_DISABLED = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /** اسم مكوّن الأيقونة (شعار + لون) كما وُلّد في البيان */
    fun aliasName(context: Context, logoKey: String, backgroundKey: String): String =
        "${context.packageName}.$ALIAS_MARKER${logoKey}_$backgroundKey"

    /**
     * كل الأيقونات البديلة مع حالة التفعيل الفعلية.
     * GET_DISABLED_COMPONENTS ضرورية: بدونها لا تُرجَع المعطّلة فلا نراها.
     */
    @Suppress("DEPRECATION")
    private fun aliases(context: Context): List<Pair<String, Boolean>> {
        val flags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_DISABLED_COMPONENTS or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.MATCH_DISABLED_COMPONENTS
            } else {
                0
            })
        val manager = context.packageManager
        val activities: List<ActivityInfo> = try {
            manager.getPackageInfo(context.packageName, flags).activities?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

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
        val target = aliasName(context, logoKey, backgroundKey)

        // 1) الأيقونة المطلوبة: إن لم نجدها في القائمة (نظرياً فقط) نجرب تفعيلها مباشرة
        if (all.isNotEmpty() && all.none { it.first == target }) {
            if (!setState(context, target, enabled = true)) {
                // الأيقونة المطلوبة غير موجودة فعلاً: لا نغيّر شيئاً إطلاقاً
                return false
            }
        } else if (all.isEmpty()) {
            return false
        } else {
            // 2) تفعيلها أولاً مع التحقق
            if (!setState(context, target, enabled = true)) return false
        }

        // 3) الآن فقط نُعطّل الباقي
        all.filter { it.first != target }.forEach { (name, enabled) ->
            if (enabled) setState(context, name, enabled = false)
        }

        // 4) شبكة أمان: لا نترك التطبيق بلا أي أيقونة أبداً
        ensureAtLeastOneEnabled(context)
        return true
    }

    /** إن لم تكن أي أيقونة مفعّلة نفعّل الأولى */
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
        val safeBackground =
            if (backgroundValue in AppLogoDefaults.backgroundKeys) backgroundValue
            else AppLogoDefaults.defaultBackground
        val safeLogo = logoKey.ifBlank { AppLogoDefaults.defaultLogo }
        val expected = aliasName(context, safeLogo, safeBackground)

        // الحالة سليمة فقط إذا كانت أيقونة واحدة مفعّلة وهي المطلوبة بالضبط.
        // إن كانت مفعّلة لكنها غير المطلوبة (تغيير لم يُطبَّق) نُطبّق المطلوبة.
        val enabled = all.filter { it.second }.map { it.first }
        if (enabled.size == 1 && enabled.first() == expected) return expected

        if (apply(context, safeLogo, safeBackground)) return expected
        return ensureAtLeastOneEnabled(context)
    }

    /** أقرب لون جاهز للّون المخصص (ترحيل الإعدادات القديمة) */
    fun nearestBackground(color: Color, backgrounds: List<AppLogoBackground>): AppLogoBackground? =
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
