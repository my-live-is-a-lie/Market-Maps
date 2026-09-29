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
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.marketmaps.app.data.AppPreferences
import com.marketmaps.app.data.AppThemeMode
import com.marketmaps.app.ui.CrashReportScreen
import com.marketmaps.app.ui.map.MapScreen
import com.marketmaps.app.ui.onboarding.OnboardingScreen
import com.marketmaps.app.ui.settings.SettingsScreen
import com.marketmaps.app.ui.theme.AccentPresets
import com.marketmaps.app.ui.theme.MarketMapsTheme
import com.marketmaps.app.util.CrashHandler
import kotlinx.coroutines.flow.first

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
            val prefs = remember { AppPreferences(context) }
            // شبكة أمان: إن اختفت أيقونة التطبيق (تغيير لم يكتمل) نعيد تفعيلها تلقائياً
            LaunchedEffect(Unit) {
                val logoKey = prefs.appLogoKey.first()
                val logoBackground = prefs.appLogoBackground.first()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        com.marketmaps.app.ui.settings.LauncherIconSwitcher
                            .ensureValidIcon(context, logoKey, logoBackground)
                    }
                }
            }
            val themeMode by prefs.themeMode.collectAsState(initial = AppThemeMode.LIGHT)
            val accentKey by prefs.accentKey.collectAsState(initial = AccentPresets.DEFAULT)

            MarketMapsTheme(themeMode = themeMode, accentKey = accentKey) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var onboardingDone by remember { mutableStateOf<Boolean?>(null) }
                    var localDone by rememberSaveable { mutableStateOf(false) }
                    // rememberSaveable: تبقى الإعدادات مفتوحة بعد تدوير الشاشة
                    var showSettings by rememberSaveable { mutableStateOf(false) }
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
                                onDismiss = {
                                    CrashHandler.clear(context)
                                    crashText = null
                                }
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
                        !(onboardingDone!! || localDone) -> {
                            OnboardingScreen(
                                onFinished = { localDone = true }
                            )
                        }
                        else -> {
                            BackHandler(enabled = showSettings) {
                                showSettings = false
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                                MapScreen(
                                    onOpenSettings = { showSettings = true },
                                    onOpenFullSettings = { showSettings = true },
                                    isCovered = showSettings
                                )
                                if (showSettings) {
                                    SettingsScreen(onBack = { showSettings = false })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
