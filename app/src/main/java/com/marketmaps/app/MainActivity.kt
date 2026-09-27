package com.marketmaps.app

import android.os.Bundle
import com.google.android.gms.maps.MapsInitializer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.marketmaps.app.data.AppPreferences
import com.marketmaps.app.ui.CrashReportScreen
import com.marketmaps.app.ui.map.MapScreen
import com.marketmaps.app.ui.onboarding.OnboardingScreen
import com.marketmaps.app.ui.settings.SettingsScreen
import com.marketmaps.app.ui.theme.MarketMapsTheme
import com.marketmaps.app.util.CrashHandler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // تهيئة مبكرة لمصنع أيقونات خرائط جوجل
        try {
            MapsInitializer.initialize(applicationContext)
        } catch (_: Exception) {
        }
        CrashHandler.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            MarketMapsApp()
        }
    }
}

@Composable
fun MarketMapsApp() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val themeMode by prefs.themeMode.collectAsState(initial = com.marketmaps.app.data.AppThemeMode.SYSTEM)
    val accentKey by prefs.accentKey.collectAsState(initial = "blue")
    val onboardingDone by prefs.onboardingDone.collectAsState(initial = false)

    MarketMapsTheme(themeMode = themeMode, accentKey = accentKey) {
        val navController = rememberNavController()
        val start = if (onboardingDone) "map" else "onboarding"
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.fillMaxSize()
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate("map") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            composable("map") {
                MapScreen(
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenCrashReport = { navController.navigate("crash") }
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("crash") {
                CrashReportScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
