package com.marketmaps.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.marketmaps.app.data.CategoryData
import com.marketmaps.app.data.Store
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<StoreWithDistance>,
    onResultClick: (Store) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    filterType: String,
    filterSub: String,
    onFilterTypeChange: (String) -> Unit,
    onFilterSubChange: (String) -> Unit,
    onOpenFilterDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // أنواع الأماكن الأساسية
    val mainFilters = remember { listOf("الكل") + CategoryData.categories.map { it.name } }

    // عند تحديد نوع مكان: عرض تصنيفاته + زر الكل للعودة
    val subFilters = remember(filterType) {
        if (filterType == "الكل") {
            emptyList()
        } else {
            val cat = CategoryData.categories.find { it.name == filterType }
            listOf("الكل") + (cat?.subCategories?.map { it.name } ?: emptyList())
        }
    }

    val showingSubs = filterType != "الكل"
    val quickFilters = if (showingSubs) subFilters else mainFilters

    val barColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val barContent = MaterialTheme.colorScheme.onSurface
    val typeColor = if (filterType == "الكل") {
        MaterialTheme.colorScheme.primary
    } else {
        Color(MarkerIconHelper.colorForCategory(filterType))
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    Column(modifier = modifier) {
        if (expanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = barColor)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { text ->
                        onQueryChange(text.replace("\n", " "))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            "ابحث عن منتج أو محل...",
                            color = barContent.copy(alpha = 0.55f)
                        )
                    },
                    leadingIcon = {
                        IconButton(
                            onClick = {
                                if (query.isNotEmpty()) onQueryChange("")
                                else onExpandedChange(false)
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = if (query.isNotEmpty()) "مسح البحث" else "إغلاق البحث",
                                tint = barContent
                            )
                        }
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onOpenFilterDialog) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = "فلتر",
                                    tint = if (filterType != "الكل" || filterSub != "الكل")
                                        MaterialTheme.colorScheme.primary
                                    else
                                        barContent
                                )
                            }
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = barContent,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(24.dp)
                            )
                        }
                    },
                    singleLine = false,
                    maxLines = 3,
                    minLines = 1,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = barColor,
                        unfocusedContainerColor = barColor,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = barContent,
                        unfocusedTextColor = barContent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { keyboard?.hide() }
                    )
                )
            }

            // شريط الفلاتر السريعة
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickFilters.forEach { name ->
                    val selected = if (showingSubs) {
                        // في وضع التصنيفات: الكل = لا تصنيف فرعي، أو الاسم = filterSub
                        if (name == "الكل") filterSub == "الكل" else filterSub == name
                    } else {
                        filterType == name
                    }
                    val selectedColor = if (showingSubs) {
                        if (name == "الكل") typeColor else Color(MarkerIconHelper.colorForCategory("$filterType $name"))
                    } else {
                        if (name == "الكل") MaterialTheme.colorScheme.primary
                        else Color(MarkerIconHelper.colorForCategory(name))
                    }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (showingSubs) {
                                if (name == "الكل") {
                                    // العودة لأنواع الأماكن الأساسية
                                    onFilterTypeChange("الكل")
                                } else {
                                    onFilterSubChange(name)
                                }
                            } else {
                                onFilterTypeChange(name)
                            }
                        },
                        label = {
                            Text(
                                name,
                                color = if (selected) Color.White else barContent
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = barColor,
                            labelColor = barContent,
                            selectedContainerColor = selectedColor,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = barContent.copy(alpha = 0.35f),
                            selectedBorderColor = selectedColor
                        )
                    )
                }
            }

            if (query.isNotBlank() || filterType != "الكل" || filterSub != "الكل") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .heightIn(max = 280.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = barColor)
                ) {
                    if (results.isEmpty()) {
                        Text(
                            text = "لا توجد نتائج",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = barContent
                        )
                    } else {
                        LazyColumn {
                            items(results) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onResultClick(item.store)
                                            onExpandedChange(false)
                                        }
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "${item.store.category} ${item.store.name}".trim(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = barContent
                                    )
                                    if (item.distanceMeters >= 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = formatDistance(item.distanceMeters),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, end = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                FloatingActionButton(
                    onClick = { onExpandedChange(true) },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Search, contentDescription = "بحث")
                }
            }
        }
    }
}

data class StoreWithDistance(
    val store: Store,
    val distanceMeters: Double
)

fun filterAndSortStores(
    stores: List<Store>,
    query: String,
    userLat: Double?,
    userLon: Double?,
    filterType: String = "الكل",
    filterSub: String = "الكل"
): List<StoreWithDistance> {
    var filtered = stores

    if (filterType != "الكل") {
        filtered = filtered.filter { store ->
            store.category.contains(filterType, ignoreCase = true)
        }
    }
    if (filterSub != "الكل") {
        filtered = filtered.filter { store ->
            store.category.contains(filterSub, ignoreCase = true)
        }
    }

    if (query.isNotBlank()) {
        val lowerQuery = query.trim().lowercase()
        filtered = filtered.filter { store ->
            store.name.lowercase().contains(lowerQuery) ||
                    store.category.lowercase().contains(lowerQuery) ||
                    store.description.lowercase().contains(lowerQuery)
        }
    } else if (filterType == "الكل" && filterSub == "الكل") {
        return emptyList()
    }

    return if (userLat != null && userLon != null) {
        filtered.map { store ->
            val dist = haversine(userLat, userLon, store.latitude, store.longitude)
            StoreWithDistance(store, dist)
        }.sortedBy { it.distanceMeters }
    } else {
        filtered.map { StoreWithDistance(it, -1.0) }
    }
}

fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

fun formatDistance(meters: Double): String {
    return if (meters < 1000) {
        "يبعد ${meters.toInt()} متر"
    } else {
        String.format("يبعد %.1f كم", meters / 1000)
    }
}
