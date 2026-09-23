package com.marketmaps.app.ui.settings

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
import com.marketmaps.app.ui.theme.AccentPresets
import kotlinx.coroutines.launch

/** أقسام الإعدادات الرئيسية */
private enum class SettingsSection {
    MAIN,
    APPEARANCE,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainScreen(
    onBack: () -> Unit,
    onOpen: (SettingsSection) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSectionCard(
                title = "مظهر التطبيق",
                subtitle = "النمط الفاتح/الغامق ولون التمييز",
                icon = Icons.Default.Settings,
                onClick = { onOpen(SettingsSection.APPEARANCE) }
            )
            SettingsSectionCard(
                title = "نوع الخريطة",
                subtitle = "OpenStreetMap أو خرائط جوجل",
                icon = Icons.Default.LocationOn,
                onClick = { onOpen(SettingsSection.MAP_TYPE) }
            )
            SettingsSectionCard(
                title = "الخرائط المحمّلة",
                subtitle = "تحميل خريطة مصر للعمل بدون إنترنت",
                icon = Icons.Default.Add,
                onClick = { onOpen(SettingsSection.OFFLINE_MAPS) }
            )
            SettingsSectionCard(
                title = "البحث والفلاتر",
                subtitle = "تذكر آخر فلتر استخدمته",
                icon = Icons.Default.List,
                onClick = { onOpen(SettingsSection.SEARCH_FILTERS) }
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettingsScreen(
    prefs: AppPreferences,
    scope: kotlinx.coroutines.CoroutineScope,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val themeMode by prefs.themeMode.collectAsState(initial = AppThemeMode.LIGHT)
    val accentKey by prefs.accentKey.collectAsState(initial = AccentPresets.DEFAULT)

    var themeMenuExpanded by remember { mutableStateOf(false) }
    var customHex by remember { mutableStateOf("#00897B") }
    var showCustomHex by remember { mutableStateOf(false) }

    val themeLabel = when (themeMode) {
        AppThemeMode.LIGHT -> "فاتح"
        AppThemeMode.DARK -> "غامق"
        AppThemeMode.AMOLED -> "مظلم (Amoled)"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مظهر التطبيق") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "نمط التطبيق", style = MaterialTheme.typography.titleLarge)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "اختر مظهر الواجهة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ExposedDropdownMenuBox(
                        expanded = themeMenuExpanded,
                        onExpandedChange = { themeMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = themeLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("نمط التطبيق") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeMenuExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = themeMenuExpanded,
                            onDismissRequest = { themeMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("فاتح") },
                                onClick = {
                                    themeMenuExpanded = false
                                    scope.launch {
                                        prefs.setThemeMode(AppThemeMode.LIGHT)
                                        Toast.makeText(context, "تم اختيار النمط الفاتح", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("غامق") },
                                onClick = {
                                    themeMenuExpanded = false
                                    scope.launch {
                                        prefs.setThemeMode(AppThemeMode.DARK)
                                        Toast.makeText(context, "تم اختيار النمط الغامق", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("مظلم (Amoled)") },
                                onClick = {
                                    themeMenuExpanded = false
                                    scope.launch {
                                        prefs.setThemeMode(AppThemeMode.AMOLED)
                                        Toast.makeText(context, "تم اختيار النمط المظلم", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Text(text = "لون التمييز", style = MaterialTheme.typography.titleLarge)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "يظهر على الأزرار والعناصر الرئيسية",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = AccentPresets.labelForKey(accentKey),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dynamicSelected = accentKey == AccentPresets.DYNAMIC
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935))
                                    ),
                                    shape = CircleShape
                                )
                                .border(
                                    width = if (dynamicSelected) 3.dp else 1.dp,
                                    color = if (dynamicSelected) MaterialTheme.colorScheme.onSurface else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    scope.launch {
                                        prefs.setAccentKey(AccentPresets.DYNAMIC)
                                        showCustomHex = false
                                        Toast.makeText(context, "لون ديناميكي من النظام", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        )
                        AccentPresets.list.forEach { preset ->
                            val selected = accentKey == preset.key
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(preset.color, CircleShape)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        scope.launch {
                                            prefs.setAccentKey(preset.key)
                                            showCustomHex = false
                                            Toast.makeText(context, preset.label, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            )
                        }
                        val customSelected = accentKey.startsWith("custom:")
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF9E9E9E), CircleShape)
                                .border(
                                    width = if (customSelected) 3.dp else 1.dp,
                                    color = if (customSelected) MaterialTheme.colorScheme.onSurface else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { showCustomHex = !showCustomHex },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("#", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (showCustomHex || accentKey.startsWith("custom:")) {
                        OutlinedTextField(
                            value = customHex,
                            onValueChange = { customHex = it },
                            label = { Text("لون مخصص (مثال #00897B)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val hex = customHex.trim().let {
                                    if (it.startsWith("#")) it else "#$it"
                                }
                                try {
                                    android.graphics.Color.parseColor(hex)
                                    scope.launch {
                                        prefs.setAccentKey("custom:$hex")
                                        Toast.makeText(context, "تم تطبيق اللون المخصص", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                    Toast.makeText(context, "صيغة اللون غير صحيحة", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تطبيق اللون المخصص")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapTypeSettingsScreen(
    prefs: AppPreferences,
    scope: kotlinx.coroutines.CoroutineScope,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mapProvider by prefs.mapProvider.collectAsState(initial = MapProvider.MAPSFORGE)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نوع الخريطة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    MapProviderOption(
                        title = "OpenStreetMap (موصى به حالياً)",
                        subtitle = "مجاني بالكامل — بدون بطاقة أو مفتاح. يعمل بالإنترنت.",
                        selected = mapProvider == MapProvider.MAPSFORGE,
                        onClick = {
                            scope.launch {
                                prefs.setMapProvider(MapProvider.MAPSFORGE)
                                Toast.makeText(context, "تم اختيار OpenStreetMap", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    MapProviderOption(
                        title = "خرائط جوجل (لاحقاً)",
                        subtitle = "أدق، لكن يحتاج مفتاح API وحساب فوترة ببطاقة. عطّل حالياً حتى تتوفر البطاقة.",
                        selected = mapProvider == MapProvider.GOOGLE,
                        onClick = {
                            Toast.makeText(
                                context,
                                "خرائط جوجل تحتاج بطاقة دفع. استخدم OpenStreetMap الآن.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineMapsSettingsScreen(
    prefs: AppPreferences,
    scope: kotlinx.coroutines.CoroutineScope,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val offlineMode by prefs.offlineMode.collectAsState(initial = false)

    var downloaded by remember { mutableStateOf(MapDownloader.listDownloaded(context)) }
    var showLoadedList by remember { mutableStateOf(false) }
    var continentId by remember { mutableStateOf<String?>(null) }
    var selectedRegion by remember { mutableStateOf<MapCatalog.MapRegion?>(null) }
    var continentMenu by remember { mutableStateOf(false) }
    var countryMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var isDownloading by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var progressPercent by remember { mutableIntStateOf(0) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun refreshList() {
        downloaded = MapDownloader.listDownloaded(context)
    }

    val filteredRegions = remember(continentId, searchQuery) {
        when {
            searchQuery.isNotBlank() -> MapCatalog.search(searchQuery)
            continentId != null -> MapCatalog.byContinent(continentId!!)
            else -> emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showLoadedList) "الخرائط المحمّلة" else "الخرائط المحمّلة") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showLoadedList) {
                            showLoadedList = false
                            selectionMode = false
                            selectedIds = emptySet()
                        } else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showLoadedList) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("بحث عن منطقة أو بلد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val list = downloaded.filter {
                    searchQuery.isBlank() ||
                        it.nameAr.contains(searchQuery, true) ||
                        it.continentAr.contains(searchQuery, true)
                }
                if (list.isEmpty()) {
                    Text("لا توجد خرائط محمّلة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    list.forEach { item ->
                        val selected = item.fileName in selectedIds
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectionMode) {
                                        selectedIds = if (selected) selectedIds - item.fileName
                                        else selectedIds + item.fileName
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.nameAr, style = MaterialTheme.typography.titleMedium)
                                    if (item.continentAr.isNotBlank()) {
                                        Text(
                                            item.continentAr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "%.1f ميجا".format(item.sizeBytes / (1024.0 * 1024.0)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selectionMode) {
                                    Text(if (selected) "✓" else "○", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (selectionMode && selectedIds.isNotEmpty()) {
                        Button(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("حذف المحدد (${selectedIds.size})")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { selectionMode = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تحديد للحذف")
                        }
                    }
                }
            } else {
                // تحميل خريطة جديدة
                Text("تحميل خريطة", style = MaterialTheme.typography.titleLarge)
                Text(
                    "المصدر المجاني يوفّر خرائط على مستوى الدولة (وليس المحافظة). اختر القارة ثم الدولة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (it.isNotBlank()) continentId = null
                    },
                    label = { Text("بحث سريع عن دولة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = continentMenu,
                    onExpandedChange = { continentMenu = it }
                ) {
                    OutlinedTextField(
                        value = MapCatalog.continents.find { it.id == continentId }?.nameAr ?: "اختر القارة",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("القارة") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = continentMenu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = continentMenu,
                        onDismissRequest = { continentMenu = false }
                    ) {
                        MapCatalog.continents.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.nameAr) },
                                onClick = {
                                    continentId = c.id
                                    selectedRegion = null
                                    searchQuery = ""
                                    continentMenu = false
                                }
                            )
                        }
                    }
                }

                if (continentId != null || searchQuery.isNotBlank()) {
                    ExposedDropdownMenuBox(
                        expanded = countryMenu,
                        onExpandedChange = { countryMenu = it }
                    ) {
                        OutlinedTextField(
                            value = selectedRegion?.nameAr ?: "اختر الدولة",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("الدولة") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryMenu) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = countryMenu,
                            onDismissRequest = { countryMenu = false }
                        ) {
                            filteredRegions.forEach { region ->
                                val already = MapDownloader.isDownloaded(context, region.fileName)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (already) "${region.nameAr} (محمّلة) — ~${region.approxSizeMb} ميجا"
                                            else "${region.nameAr} — ~${region.approxSizeMb} ميجا"
                                        )
                                    },
                                    onClick = {
                                        selectedRegion = region
                                        countryMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                selectedRegion?.let { region ->
                    val already = MapDownloader.isDownloaded(context, region.fileName)
                    val pending = MapDownloader.pendingTempBytes(context, region.fileName)
                    Text(
                        "${region.nameAr} — ${region.continentAr} — تقريباً ${region.approxSizeMb} ميجا",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (pending > 0 && !already) {
                        Text(
                            "يمكن استئناف التحميل من %.1f ميجا".format(pending / (1024.0 * 1024.0)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(progressText, style = MaterialTheme.typography.bodySmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isPaused) {
                                        MapDownloader.resume()
                                        isPaused = false
                                    } else {
                                        MapDownloader.pause()
                                        isPaused = true
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isPaused) "استئناف" else "إيقاف مؤقت")
                            }
                            Button(
                                onClick = {
                                    MapDownloader.cancel()
                                    isDownloading = false
                                    isPaused = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إلغاء")
                            }
                        }
                    } else if (!already) {
                        Button(
                            onClick = {
                                isDownloading = true
                                isPaused = false
                                progressPercent = 0
                                progressText = "جاري البدء..."
                                scope.launch {
                                    val result = MapDownloader.download(context, region) { p ->
                                        progressPercent = p.percent
                                        progressText = p.formatLine()
                                    }
                                    isDownloading = false
                                    isPaused = false
                                    if (result.isSuccess) {
                                        refreshList()
                                        Toast.makeText(context, "تم تحميل ${region.nameAr}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            result.exceptionOrNull()?.message ?: "فشل التحميل",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (pending > 0) "استئناف التحميل" else "تحميل ${region.nameAr}")
                        }
                    } else {
                        Text("هذه الخريطة محمّلة بالفعل", color = MaterialTheme.colorScheme.primary)
                    }
                }

                // زر قائمة المحمّل
                OutlinedButton(
                    onClick = {
                        refreshList()
                        searchQuery = ""
                        showLoadedList = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("الخرائط المحمّلة (${downloaded.size})")
                }

                // وضع بدون إنترنت
                val hasAny = downloaded.isNotEmpty() || MapDownloader.isEgyptMapDownloaded(context)
                if (hasAny) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("وضع بدون إنترنت", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "عرض الخريطة المحمّلة محلياً",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = offlineMode,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        prefs.setOfflineMode(enabled)
                                        Toast.makeText(
                                            context,
                                            if (enabled) "تم تفعيل الوضع بدون إنترنت" else "تم تفعيل الوضع بالإنترنت",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("تأكيد الحذف") },
                text = {
                    val size = downloaded.filter { it.fileName in selectedIds }.sumOf { it.sizeBytes }
                    val sizeMb = size / (1024.0 * 1024.0)
                    val msg = "سيتم حذف ${selectedIds.size} منطقة\nالحجم تقريباً " + "%.1f".format(sizeMb) + " ميجا"
                    Text(msg)
                },
                confirmButton = {
                    Button(onClick = {
                        MapDownloader.deleteMaps(context, selectedIds.toList())
                        refreshList()
                        selectedIds = emptySet()
                        selectionMode = false
                        showDeleteConfirm = false
                        Toast.makeText(context, "تم الحذف", Toast.LENGTH_SHORT).show()
                    }) { Text("تأكيد") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("إلغاء") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilterSettingsScreen(
    prefs: AppPreferences,
    scope: kotlinx.coroutines.CoroutineScope,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val rememberFilter by prefs.rememberFilter.collectAsState(initial = false)
    val recentLimit by prefs.recentSearchLimit.collectAsState(initial = 5)
    var limitMenuExpanded by remember { mutableStateOf(false) }

    val limitLabel = when (recentLimit) {
        0 -> "معطل"
        else -> "$recentLimit نتيجة"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("البحث والفلاتر") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تذكر إعدادات الفلتر", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "عند التفعيل يُحفظ آخر فلتر استخدمته. عند الإيقاف يعود للـ«الكل» في كل مرة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rememberFilter,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                prefs.setRememberFilter(enabled)
                                Toast.makeText(
                                    context,
                                    if (enabled) "سيتم تذكر الفلتر" else "الفلتر سيعود للافتراضي",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }

            Text("سجل البحث", style = MaterialTheme.typography.titleLarge)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "عدد نتائج البحث السابقة في شريط البحث",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = limitMenuExpanded,
                        onExpandedChange = { limitMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = limitLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("عدد النتائج السابقة") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = limitMenuExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = limitMenuExpanded,
                            onDismissRequest = { limitMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("معطل") },
                                onClick = {
                                    limitMenuExpanded = false
                                    scope.launch {
                                        prefs.setRecentSearchLimit(0)
                                        Toast.makeText(context, "تم تعطيل سجل البحث", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            (1..8).forEach { n ->
                                DropdownMenuItem(
                                    text = { Text("$n نتيجة") },
                                    onClick = {
                                        limitMenuExpanded = false
                                        scope.launch {
                                            prefs.setRecentSearchLimit(n)
                                            Toast.makeText(context, "سيظهر $n من عمليات البحث السابقة", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                prefs.clearRecentSearches()
                                Toast.makeText(context, "تم مسح سجل البحث", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("مسح سجل البحث")
                    }
                }
            }
        }
    }
}

@Composable
private fun MapProviderOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
