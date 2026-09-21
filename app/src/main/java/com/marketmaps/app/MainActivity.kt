package com.marketmaps.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)
        enableEdgeToEdge()
        setContent {
            MarketMapsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val prefs = remember { AppPreferences(context) }

                    // null = لم تُحمَّل التفضيلات بعد (يمنع وميض شاشة أول استخدام)
                    var onboardingDone by remember { mutableStateOf<Boolean?>(null) }
                    var localDone by remember { mutableStateOf(false) }
                    var showSettings by remember { mutableStateOf(false) }
                    var crashText by remember {
                        mutableStateOf(CrashHandler.readLastCrash(context))
                    }

                    LaunchedEffect(Unit) {
                        onboardingDone = prefs.onboardingDone.first()
                    }

                    when {
                        crashText != null -> {
                            CrashReportScreen(
                                crashText = crashText!!,
                                onDismiss = { crashText = null }
                            )
                        }
                        onboardingDone == null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        !(onboardingDone == true || localDone) -> {
                            OnboardingScreen(onFinished = { localDone = true })
                        }
                        showSettings -> {
                            SettingsScreen(onBack = { showSettings = false })
                        }
                        else -> {
                            MapScreen(onOpenSettings = { showSettings = true })
                        }
                    }
                }
            }
        }
    }
}
