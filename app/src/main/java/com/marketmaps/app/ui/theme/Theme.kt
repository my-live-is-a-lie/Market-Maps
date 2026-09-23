package com.marketmaps.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.marketmaps.app.data.AppThemeMode

private fun onAccent(accent: Color): Color {
    return if (accent.luminance() > 0.55f) Color.Black else Color.White
}

private fun lighten(color: Color, factor: Float = 0.85f): Color {
    return Color(
        red = color.red + (1f - color.red) * factor,
        green = color.green + (1f - color.green) * factor,
        blue = color.blue + (1f - color.blue) * factor,
        alpha = 1f
    )
}

private fun buildLightScheme(accent: Color): ColorScheme {
    val onA = onAccent(accent)
    return lightColorScheme(
        primary = accent,
        onPrimary = onA,
        secondary = accent,
        onSecondary = onA,
        tertiary = accent,
        onTertiary = onA,
        primaryContainer = lighten(accent, 0.75f),
        onPrimaryContainer = Color(0xFF1A1A1A),
        secondaryContainer = lighten(accent, 0.7f),
        onSecondaryContainer = Color(0xFF1A1A1A),
        tertiaryContainer = lighten(accent, 0.65f),
        onTertiaryContainer = Color(0xFF1A1A1A),
        background = LightBackground,
        surface = LightSurface,
        onBackground = LightOnBackground,
        onSurface = LightOnSurface,
        surfaceContainerHighest = Color(0xFFE8E8E8),
        onSurfaceVariant = Color(0xFF444444)
    )
}

private fun buildDarkScheme(accent: Color, amoled: Boolean): ColorScheme {
    val onA = onAccent(accent)
    val bg = if (amoled) AmoledBackground else DarkBackground
    val surface = if (amoled) AmoledSurface else DarkSurface
    // في الوضع المظلم: الأزرار بلون التمييز نفسه حتى تبقى واضحة
    return darkColorScheme(
        primary = accent,
        onPrimary = onA,
        secondary = accent,
        onSecondary = onA,
        tertiary = accent,
        onTertiary = onA,
        primaryContainer = accent,
        onPrimaryContainer = onA,
        secondaryContainer = accent,
        onSecondaryContainer = onA,
        tertiaryContainer = accent,
        onTertiaryContainer = onA,
        background = bg,
        surface = surface,
        onBackground = Color.White,
        onSurface = Color.White,
        surfaceContainerHighest = if (amoled) Color(0xFF1A1A1A) else Color(0xFF2C2C2C),
        onSurfaceVariant = Color(0xFFB0B0B0)
    )
}

@Composable
fun MarketMapsTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    accentKey: String = AccentPresets.DEFAULT,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = themeMode == AppThemeMode.DARK || themeMode == AppThemeMode.AMOLED
    val amoled = themeMode == AppThemeMode.AMOLED

    val colorScheme = when {
        accentKey == AccentPresets.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // نأخذ primary من الديناميكي ونبني بقية المخطط حسب النمط
            if (isDark) buildDarkScheme(dynamic.primary, amoled) else buildLightScheme(dynamic.primary)
        }
        else -> {
            val accent = AccentPresets.colorForKey(accentKey)
                ?: AccentPresets.list.first().color
            if (isDark) buildDarkScheme(accent, amoled) else buildLightScheme(accent)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
