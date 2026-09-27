package com.marketmaps.app

import android.os.Bundle
import com.google.android.gms.maps.MapsInitializer
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.marketmaps.app.data.AppPreferences
import com.marketmaps.app.ui.CrashReportScreen
import com.marketmaps.app.ui.map.MapScreen
import com.marketmaps.app.ui.onboarding.OnboardingScreen
import com.marketmaps.app.ui.settings.SettingsScreen
import com.marketmaps.app.ui.theme.MarketMapsTheme
import com.marketmaps.app.util.CrashHandler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            MapsInitializer.initialize(applicationContext)
        } catch (_: Exception) {
        }
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            CrashHandler.install(context)
            val prefs = remember { AppPreferences(context) }
            val settings by prefs.settings.collectAsState(initial = null)
            if (settings == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                MarketMapsApp(prefs = prefs, settings = settings!!)
            }
        }
    }
}

@Composable
private fun MarketMapsApp(
    prefs: AppPreferences,
    settings: com.marketmaps.app.data.AppSettings
) {
    var showSettings by remember { mutableStateOf(false) }
    var showCrash by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MarketMapsTheme(
        themeMode = settings.themeMode,
        accentKey = settings.accentKey
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                showCrash -> {
                    CrashReportScreen(onBack = { showCrash = false })
                }
                !settings.onboardingDone -> {
                    OnboardingScreen(
                        onFinished = {
                            scope.launch { prefs.setOnboardingDone(true) }
                        }
                    )
                }
                showSettings -> {
                    SettingsScreen(onBack = { showSettings = false })
                }
                else -> {
                    MapScreen(
                        onOpenSettings = { showSettings = true },
                        onOpenCrashReport = { showCrash = true }
                    )
                }
            }
        }
    }

    BackHandler(enabled = showSettings || showCrash) {
        when {
            showCrash -> showCrash = false
            showSettings -> showSettings = false
        }
    }
}
