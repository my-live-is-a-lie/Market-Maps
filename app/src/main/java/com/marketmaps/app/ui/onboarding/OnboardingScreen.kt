package com.marketmaps.app.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.marketmaps.app.data.AppPreferences
import com.marketmaps.app.data.EgyptLocations
import kotlinx.coroutines.launch

/**
 * شاشة أول استخدام: تحديد الموقع عبر GPS أو اختيار يدوي.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context) }

    var selectedGov by remember { mutableStateOf<EgyptLocations.Governorate?>(null) }
    var selectedArea by remember { mutableStateOf<EgyptLocations.Area?>(null) }
    var expandedGov by remember { mutableStateOf(false) }
    var expandedArea by remember { mutableStateOf(false) }
    var isLoadingGps by remember { mutableStateOf(false) }

    fun finishWithLocation(lat: Double, lon: Double, label: String) {
        scope.launch {
            prefs.saveLastLocation(lat, lon, 14.0, label)
            prefs.setOnboardingDone(true)
            onFinished()
        }
    }

    @SuppressLint("MissingPermission")
    fun useGps() {
        isLoadingGps = true
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                isLoadingGps = false
                if (location != null) {
                    finishWithLocation(location.latitude, location.longitude, "موقعي الحالي")
                } else {
                    Toast.makeText(context, "تعذر الحصول على الموقع، جرّب الاختيار اليدوي", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                isLoadingGps = false
                Toast.makeText(context, "حدث خطأ أثناء تحديد الموقع", Toast.LENGTH_LONG).show()
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            useGps()
        } else {
            Toast.makeText(context, "يلزم السماح بالموقع لاستخدام GPS", Toast.LENGTH_LONG).show()
        }
    }

    fun requestGps() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            useGps()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "مرحباً بك في Market Maps",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "حدد موقعك لنبدأ بعرض المحلات القريبة منك.
يمكنك لاحقاً تحميل الخريطة للعمل بدون إنترنت من الإعدادات.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        // زر GPS
        Button(
            onClick = { requestGps() },
            enabled = !isLoadingGps,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = null)
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
            Text(if (isLoadingGps) "جاري تحديد الموقع..." else "استخدام موقعي الحالي")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "أو اختر يدوياً",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        // الدولة (ثابتة حالياً)
        OutlinedTextField(
            value = "مصر",
            onValueChange = {},
            readOnly = true,
            label = { Text("الدولة") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // المحافظة
        ExposedDropdownMenuBox(
            expanded = expandedGov,
            onExpandedChange = { expandedGov = it }
        ) {
            OutlinedTextField(
                value = selectedGov?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("المحافظة") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGov) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expandedGov,
                onDismissRequest = { expandedGov = false }
            ) {
                EgyptLocations.governorates.forEach { gov ->
                    DropdownMenuItem(
                        text = { Text(gov.name) },
                        onClick = {
                            selectedGov = gov
                            selectedArea = null
                            expandedGov = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // المنطقة
        if (selectedGov != null) {
            ExposedDropdownMenuBox(
                expanded = expandedArea,
                onExpandedChange = { expandedArea = it }
            ) {
                OutlinedTextField(
                    value = selectedArea?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("المنطقة") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedArea) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedArea,
                    onDismissRequest = { expandedArea = false }
                ) {
                    selectedGov?.areas?.forEach { area ->
                        DropdownMenuItem(
                            text = { Text(area.name) },
                            onClick = {
                                selectedArea = area
                                expandedArea = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val area = selectedArea
                if (area != null) {
                    val label = "${selectedGov?.name} - ${area.name}"
                    finishWithLocation(area.latitude, area.longitude, label)
                }
            },
            enabled = selectedArea != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("متابعة")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                // تخطي: القاهرة كافتراضي
                finishWithLocation(30.0444, 31.2357, "القاهرة")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("تخطي الآن")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "لن يتم تحميل الخريطة تلقائياً على جهازك. يمكنك تحميلها لاحقاً من الإعدادات إن أردت العمل بدون إنترنت.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
