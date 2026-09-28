package com.marketmaps.app.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * تبديل أيقونة الشاشة الرئيسية.
 *
 * نظام أندرويد لا يسمح بتغيير أيقونة المشغّل مباشرة، بل عبر مكوّنات «أيقونة بديلة»
 * (activity-alias) تُعلَن مسبقاً في البيان — يولّدها البناء لكل تركيبة شعار × لون —
 * ثم يفعّل التطبيق واحداً منها ويُعطّل الباقي حسب اختيار المستخدم.
 * (الألوان المتاحة للأيقونة هي القائمة الجاهزة، إذ تُبنى مواردها وقت البناء.)
 */
object LauncherIconSwitcher {

    private const val ALIAS_MARKER = "LauncherIcon_"

    fun aliasFor(context: Context, logoKey: String, backgroundKey: String): String =
        "${context.packageName}.$ALIAS_MARKER${logoKey}_$backgroundKey"

    /**
     * يفعّل أيقونة (شعار + لون) ويُعطّل بقية الأيقونات البديلة.
     * @return true إذا وُجدت الأيقونة المطلوبة وفُعّلت.
     */
    fun apply(context: Context, logoKey: String, backgroundKey: String): Boolean {
        if (logoKey.isBlank() || backgroundKey.isBlank()) return false
        val packageName = context.packageName
        val target = aliasFor(context, logoKey, backgroundKey)
        val manager = context.packageManager
        val activities = try {
            manager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES).activities
        } catch (_: Exception) {
            null
        } ?: return false

        var applied = false
        activities.forEach { activity ->
            val name = activity.name ?: return@forEach
            if (!name.contains(ALIAS_MARKER)) return@forEach
            val component = ComponentName(packageName, name)
            val shouldEnable = name == target
            val desired = if (shouldEnable) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val current = try {
                manager.getComponentEnabledSetting(component)
            } catch (_: Exception) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }
            if (current != desired) {
                try {
                    // DONT_KILL_APP: لا نُغلق التطبيق أثناء التبديل
                    manager.setComponentEnabledSetting(component, desired, PackageManager.DONT_KILL_APP)
                } catch (_: Exception) {
                    // بعض المشغّلات/إصدارات النظام قد ترفض التبديل: نتجاهل بهدوء
                }
            }
            if (shouldEnable) applied = true
        }
        return applied
    }

    /**
     * أقرب لون جاهز للون المخصص: أيقونة الشاشة الرئيسية تدعم الألوان الجاهزة فقط
     * (مواردها تُبنى وقت البناء)، فنستخدم أقرب لون بدل تجاهل الاختيار.
     */
    fun nearestBackground(color: Color, backgrounds: List<AppLogoBackground>): AppLogoBackground? =
        backgrounds.minByOrNull { background ->
            val dr = (background.color.red - color.red).toDouble()
            val dg = (background.color.green - color.green).toDouble()
            val db = (background.color.blue - color.blue).toDouble()
            dr * dr + dg * dg + db * db
        }

    /** هل اللون المخصص قريب جداً من أحد الألوان الجاهزة؟ */
    fun isCloseToPreset(color: Color, preset: Color, tolerance: Float = 0.06f): Boolean =
        abs(color.red - preset.red) < tolerance &&
            abs(color.green - preset.green) < tolerance &&
            abs(color.blue - preset.blue) < tolerance
}
