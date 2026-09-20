package com.marketmaps.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.unit.dp
import com.marketmaps.app.data.CategoryData

/**
 * نافذة إضافة محل جديد.
 * تحتوي على:
 * - اسم المحل (إجباري)
 * - تصنيف هرمي (مستوى 1 ← 2 ← 3 إن وُجد)
 * - وصف قصير (اختياري)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStoreDialog(
    latitude: Double,
    longitude: Double,
    onDismiss: () -> Unit,
    onSave: (name: String, categoryPath: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // المستوى الأول
    var selectedLevel1 by remember { mutableStateOf<CategoryData.Category?>(null) }
    var expanded1 by remember { mutableStateOf(false) }

    // المستوى الثاني
    var selectedLevel2 by remember { mutableStateOf<CategoryData.SubCategory?>(null) }
    var expanded2 by remember { mutableStateOf(false) }

    // المستوى الثالث
    var selectedLevel3 by remember { mutableStateOf<String?>(null) }
    var expanded3 by remember { mutableStateOf(false) }

    val isValid = name.isNotBlank() && selectedLevel1 != null && selectedLevel2 != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة محل جديد") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // اسم المحل
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المحل *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // المستوى الأول
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

                // المستوى الثاني
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

                // المستوى الثالث (إن وُجد)
                if (selectedLevel2 != null && selectedLevel2!!.thirdLevel.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded3,
                        onExpandedChange = { expanded3 = it }
                    ) {
                        OutlinedTextField(
                            value = selectedLevel3 ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("تفاصيل إضافية") },
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

                // الوصف
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("وصف قصير (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Text(
                    text = "الموقع: ${String.format("%.5f", latitude)}, ${String.format("%.5f", longitude)}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val categoryPath = buildString {
                        append(selectedLevel1?.name ?: "")
                        append(" ")
                        append(selectedLevel2?.name ?: "")
                        if (!selectedLevel3.isNullOrBlank()) {
                            append(" ")
                            append(selectedLevel3)
                        }
                    }.trim()
                    onSave(name.trim(), categoryPath, description.trim())
                },
                enabled = isValid
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
