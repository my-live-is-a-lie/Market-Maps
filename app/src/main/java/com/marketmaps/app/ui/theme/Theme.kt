package com.marketmaps.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.marketmaps.app.data.AppThemeMode

private val LightScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    surfaceContainerHighest = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF444444)
)

private val DarkScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color(0xFF1A1A1A),
    onSecondary = Color(0xFF1A1A1A),
    onTertiary = Color(0xFF1A1A1A),
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    surfaceContainerHighest = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB0B0B0)
)

private val AmoledScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = AmoledBackground,
    surface = AmoledSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = AmoledOnBackground,
    onSurface = AmoledOnSurface,
    surfaceContainerHighest = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFB0B0B0)
)

@Composable
fun MarketMapsTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.LIGHT -> LightScheme
        AppThemeMode.DARK -> DarkScheme
        AppThemeMode.AMOLED -> AmoledScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
