package com.marketmaps.app.ui.map

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.marketmaps.app.data.CategoryData
import com.marketmaps.app.data.Store
import java.util.Locale

private const val MAX_PHOTOS = 3

/**
 * رفع الصور يحتاج Firebase Storage (خطة Blaze).
 * اجعلها true بعد تفعيل Storage وربط الفوترة.
 */
private const val ENABLE_STORE_PHOTOS = false

/**
 * نافذة إضافة أو تعديل موقع — التصنيف من القوائم.
 * الصور اختيارية وتظهر فقط عند تفعيل [ENABLE_STORE_PHOTOS].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStoreDialog(
    latitude: Double,
    longitude: Double,
    initialStore: Store? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        categoryPath: String,
        description: String,
        newPhotoUris: List<Uri>,
        existingPhotoUrls: List<String>,
        onComplete: (success: Boolean) -> Unit
    ) -> Unit
) {
    val isEditMode = initialStore != null
    val parsed = remember(initialStore?.id, initialStore?.category) {
        parseCategoryPath(initialStore?.category.orEmpty())
    }

    var isSaving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(initialStore?.name ?: "") }
    var description by remember { mutableStateOf(initialStore?.description ?: "") }

    var selectedLevel1 by remember { mutableStateOf(parsed.first) }
    var expanded1 by remember { mutableStateOf(false) }
    var selectedLevel2 by remember { mutableStateOf(parsed.second) }
    var expanded2 by remember { mutableStateOf(false) }
    var selectedLevel3 by remember { mutableStateOf(parsed.third) }
    var expanded3 by remember { mutableStateOf(false) }

    var existingPhotos by remember {
        mutableStateOf(initialStore?.photoUrls.orEmpty())
    }
    var newPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val slotsLeft = (MAX_PHOTOS - existingPhotos.size - newPhotoUris.size).coerceAtLeast(0)

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PHOTOS)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val room = (MAX_PHOTOS - existingPhotos.size - newPhotoUris.size).coerceAtLeast(0)
        if (room <= 0) return@rememberLauncherForActivityResult
        newPhotoUris = (newPhotoUris + uris).distinct().take(newPhotoUris.size + room)
    }

    val needsLevel3 = selectedLevel2 != null && selectedLevel2!!.thirdLevel.isNotEmpty()
    val isValid = name.isNotBlank() && selectedLevel1 != null && selectedLevel2 != null &&
        (!needsLevel3 || !selectedLevel3.isNullOrBlank())

    fun buildCategoryPath(): String {
        val parts = mutableListOf<String>()
        selectedLevel1?.name?.let { parts.add(it) }
        selectedLevel2?.name?.let { parts.add(it) }
        if (needsLevel3) selectedLevel3?.let { parts.add(it) }
        return parts.joinToString(" ")
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (isEditMode) "تعديل موقع" else "إضافة موقع جديد") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المكان *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expanded1,
                    onExpandedChange = { expanded1 = !expanded1 }
                ) {
                    OutlinedTextField(
                        value = selectedLevel1?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("نوع المكان *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded1) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded1,
                        onDismissRequest = { expanded1 = false }
                    ) {
                        CategoryData.categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedLevel1 = cat
                                    selectedLevel2 = null
                                    selectedLevel3 = null
                                    expanded1 = false
                                }
                            )
                        }
                    }
                }

                if (selectedLevel1 != null) {
                    ExposedDropdownMenuBox(
                        expanded = expanded2,
                        onExpandedChange = { expanded2 = !expanded2 }
                    ) {
                        OutlinedTextField(
                            value = selectedLevel2?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("التصنيف *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded2) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded2,
                            onDismissRequest = { expanded2 = false }
                        ) {
                            selectedLevel1?.subCategories?.forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub.name) },
                                    onClick = {
                                        selectedLevel2 = sub
                                        selectedLevel3 = null
                                        expanded2 = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (needsLevel3) {
                    ExposedDropdownMenuBox(
                        expanded = expanded3,
                        onExpandedChange = { expanded3 = !expanded3 }
                    ) {
                        OutlinedTextField(
                            value = selectedLevel3 ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("تفصيل إضافي *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded3) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded3,
                            onDismissRequest = { expanded3 = false }
                        ) {
                            selectedLevel2?.thirdLevel?.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        selectedLevel3 = item
                                        expanded3 = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف قصير (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                if (ENABLE_STORE_PHOTOS) {
                    Text(
                        text = "صور المكان (اختياري — حتى $MAX_PHOTOS)",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        existingPhotos.forEach { url ->
                            PhotoThumb(
                                model = url,
                                onRemove = { existingPhotos = existingPhotos.filter { it != url } }
                            )
                        }
                        newPhotoUris.forEach { uri ->
                            PhotoThumb(
                                model = uri,
                                onRemove = { newPhotoUris = newPhotoUris.filter { it != uri } }
                            )
                        }
                        if (slotsLeft > 0) {
                            OutlinedButton(
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.size(72.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "إضافة صورة")
                            }
                        }
                    }
                }

                Text(
                    text = String.format(
                        Locale.US,
                        "الموقع: %.5f, %.5f",
                        latitude,
                        longitude
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    onSave(
                        name.trim(),
                        buildCategoryPath(),
                        description.trim(),
                        newPhotoUris,
                        existingPhotos
                    ) { _ ->
                        isSaving = false
                    }
                },
                enabled = isValid && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (isEditMode) "تحديث" else "حفظ")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun PhotoThumb(model: Any, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray.copy(alpha = 0.4f))
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .padding(2.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "حذف",
                tint = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(2.dp)
            )
        }
    }
}

/** تفكيك نص التصنيف المحفوظ إلى مستويات القائمة */
private fun parseCategoryPath(
    path: String
): Triple<CategoryData.Category?, CategoryData.SubCategory?, String?> {
    val trimmed = path.trim()
    if (trimmed.isEmpty()) return Triple(null, null, null)
    val cat = CategoryData.categories
        .sortedByDescending { it.name.length }
        .find { trimmed == it.name || trimmed.startsWith(it.name + " ") }
        ?: return Triple(null, null, null)
    val rest = trimmed.removePrefix(cat.name).trim()
    if (rest.isEmpty()) return Triple(cat, null, null)
    val sub = cat.subCategories
        .sortedByDescending { it.name.length }
        .find { rest == it.name || rest.startsWith(it.name + " ") }
        ?: return Triple(cat, null, null)
    val third = rest.removePrefix(sub.name).trim().ifBlank { null }
    val thirdOk = if (third != null && sub.thirdLevel.isNotEmpty()) {
        sub.thirdLevel.find { it == third } ?: third
    } else third
    return Triple(cat, sub, thirdOk)
}
