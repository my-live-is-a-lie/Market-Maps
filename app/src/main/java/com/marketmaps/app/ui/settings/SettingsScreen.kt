package com.marketmaps.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.marketmaps.app.data.AppPreferences
import com.marketmaps.app.data.MapDownloader
import com.marketmaps.app.data.MapProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context) }

    val offlineMode by prefs.offlineMode.collectAsState(initial = false)
    val mapProvider by prefs.mapProvider.collectAsState(initial = MapProvider.GOOGLE)
    var mapDownloaded by remember { mutableStateOf(MapDownloader.isEgyptMapDownloaded(context)) }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "نوع الخريطة",
                style = MaterialTheme.typography.titleLarge
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    MapProviderOption(
                        title = "خرائط جوجل (موصى به)",
                        subtitle = "أدق وأكثر وضوحاً — يحتاج إنترنت",
                        selected = mapProvider == MapProvider.GOOGLE,
                        onClick = {
                            scope.launch {
                                prefs.setMapProvider(MapProvider.GOOGLE)
                                prefs.setOfflineMode(false)
                                Toast.makeText(context, "تم اختيار خرائط جوجل", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    MapProviderOption(
                        title = "Mapsforge",
                        subtitle = "يعمل بدون إنترنت بعد تحميل الخريطة",
                        selected = mapProvider == MapProvider.MAPSFORGE,
                        onClick = {
                            scope.launch {
                                prefs.setMapProvider(MapProvider.MAPSFORGE)
                                Toast.makeText(context, "تم اختيار Mapsforge", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            Text(
                text = "ملاحظة: خرائط جوجل تحتاج مفتاح API من Google Cloud. راجع ملف README.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "الخريطة بدون إنترنت (Mapsforge)",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "تحميل خريطة مصر (~173 ميجا) لاستخدام Mapsforge بدون اتصال.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (mapDownloaded) "✓ خريطة مصر محمّلة" else "خريطة مصر غير محمّلة",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isDownloading) {
                        Text(text = "جاري التحميل... $progress%")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (!mapDownloaded) {
                        Button(
                            onClick = {
                                isDownloading = true
                                progress = 0
                                scope.launch {
                                    val result = MapDownloader.downloadEgyptMap(context) { p ->
                                        progress = p
                                    }
                                    isDownloading = false
                                    if (result.isSuccess) {
                                        mapDownloaded = true
                                        prefs.setMapFileName(MapDownloader.EGYPT_MAP_FILE)
                                        Toast.makeText(context, "تم تحميل الخريطة بنجاح", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "فشل التحميل: ${result.exceptionOrNull()?.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تحميل خريطة مصر")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                MapDownloader.deleteEgyptMap(context)
                                mapDownloaded = false
                                scope.launch {
                                    prefs.setOfflineMode(false)
                                    prefs.setMapFileName("")
                                }
                                Toast.makeText(context, "تم حذف الخريطة", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("حذف الخريطة المحمّلة")
                        }
                    }
                }
            }

            if (mapProvider == MapProvider.MAPSFORGE) {
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
                                text = if (mapDownloaded)
                                    "عند التفعيل تُستخدم الخريطة المحمّلة"
                                else
                                    "حمّل الخريطة أولاً",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = offlineMode && mapDownloaded,
                            enabled = mapDownloaded && !isDownloading,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    prefs.setOfflineMode(enabled)
                                    Toast.makeText(
                                        context,
                                        if (enabled) "وضع بدون إنترنت" else "وضع الإنترنت",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }

            if (isDownloading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
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
