package com.marketmaps.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marketmaps.app.data.CategoryData
import com.marketmaps.app.data.Store
import java.util.Locale

/**
 * نافذة إضافة أو تعديل موقع — التصنيف دائماً من القوائم المنسدلة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStoreDialog(
    latitude: Double,
    longitude: Double,
    initialStore: Store? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, categoryPath: String, description: String, onComplete: (success: Boolean) -> Unit) -> Unit
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

    val needsLevel3 = selectedLevel2 != null && selectedLevel2!!.thirdLevel.isNotEmpty()
    val isValid = name.isNotBlank() &&
        selectedLevel1 != null &&
        selectedLevel2 != null &&
        (!needsLevel3 || !selectedLevel3.isNullOrBlank())

    fun buildCategoryPath(): String = buildString {
        append(selectedLevel1?.name ?: "")
        append(" ")
        append(selectedLevel2?.name ?: "")
        if (!selectedLevel3.isNullOrBlank()) {
            append(" ")
            append(selectedLevel3)
        }
    }.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "تعديل الموقع" else "إضافة موقع جديد") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المكان *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expanded1,
                    onExpandedChange = { expanded1 = it }
                ) {
                    OutlinedTextField(
                        value = selectedLevel1?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("نوع المكان *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded1) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded1,
                        onDismissRequest = { expanded1 = false }
                    ) {
                        CategoryData.categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedLevel1 = category
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
                        onExpandedChange = { expanded2 = it }
                    ) {
                        OutlinedTextField(
                            value = selectedLevel2?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("التصنيف *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded2) },
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
                        onExpandedChange = { expanded3 = it }
                    ) {
                        OutlinedTextField(
                            value = selectedLevel3 ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("تفاصيل إضافية *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded3) },
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
                    onSave(name.trim(), buildCategoryPath(), description.trim()) { _ ->
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
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
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
