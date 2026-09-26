package com.marketmaps.app.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.marketmaps.app.data.AppPreferences
import com.marketmaps.app.data.AppThemeMode
import com.marketmaps.app.data.MapCatalog
import com.marketmaps.app.data.MapDownloader
import com.marketmaps.app.data.MapProvider
import com.marketmaps.app.data.DrawerSide
import com.marketmaps.app.data.EdgeSwipeSide
import com.marketmaps.app.ui.theme.AccentPresets
import kotlinx.coroutines.launch

/** أقسام الإعدادات الرئيسية */
private enum class SettingsSection {
    MAIN,
    APPEARANCE,
    CUSTOMIZATION,
    GESTURES,
    MAP_TYPE,
    OFFLINE_MAPS,
    SEARCH_FILTERS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context) }

    var section by remember { mutableStateOf(SettingsSection.MAIN) }

    // زر الرجوع في الهاتف: يرجع للقسم الرئيسي أولاً ثم يغلق الإعدادات
    BackHandler {
        if (section != SettingsSection.MAIN) {
            section = SettingsSection.MAIN
        } else {
            onBack()
        }
    }

    when (section) {
        SettingsSection.MAIN -> SettingsMainScreen(
            onBack = onBack,
            onOpen = { section = it }
        )
        SettingsSection.APPEARANCE -> AppearanceSettingsScreen(
            prefs = prefs,
            scope = scope,
            onBack = { section = SettingsSection.MAIN }
        )
        SettingsSection.CUSTOMIZATION -> CustomizationSettingsScreen(
            prefs = prefs,
            scope = scope,
            onBack = { section = SettingsSection.MAIN }
        )
        SettingsSection.GESTURES -> GesturesSettingsScreen(
            prefs = prefs,
            scope = scope,
            onBack = { section = SettingsSection.MAIN }
        )
        SettingsSection.MAP_TYPE -> MapTypeSettingsScreen(
            prefs = prefs,
            scope = scope,
            onBack = { section = SettingsSection.MAIN }
        )
        SettingsSection.OFFLINE_MAPS -> OfflineMapsSettingsScreen(
            prefs = prefs,
            scope = scope,
            onBack = { section = SettingsSection.MAIN }
        )
        SettingsSection.SEARCH_FILTERS -> SearchFilterSettingsScreen(
            prefs = prefs,
            scope = scope,
            onBack = { section = SettingsSection.MAIN }
        )
    }
}
