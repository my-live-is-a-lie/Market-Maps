package com.marketmaps.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marketmaps.app.data.Store
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * مربع البحث + قائمة النتائج.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<StoreWithDistance>,
    onResultClick: (Store) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("ابحث عن منتج أو محل...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        if (query.isNotBlank() && results.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 280.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                LazyColumn {
                    items(results) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResultClick(item.store) }
                                .padding(16.dp)
                        ) {
                            Text(
                                text = item.store.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = item.store.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
        } else if (query.isNotBlank() && results.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "لا توجد نتائج",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

data class StoreWithDistance(
    val store: Store,
    val distanceMeters: Double // -1 إذا لم يتوفر موقع المستخدم
)

fun filterAndSortStores(
    stores: List<Store>,
    query: String,
    userLat: Double?,
    userLon: Double?
): List<StoreWithDistance> {
    if (query.isBlank()) return emptyList()

    val lowerQuery = query.trim().lowercase()

    val filtered = stores.filter { store ->
        store.name.lowercase().contains(lowerQuery) ||
                store.category.lowercase().contains(lowerQuery) ||
                store.description.lowercase().contains(lowerQuery)
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

/** حساب المسافة بالمتر باستخدام صيغة Haversine */
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
        "${meters.toInt()} متر"
    } else {
        String.format("%.1f كم", meters / 1000)
    }
}
