package com.marketmaps.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SearchResultNav(
    currentIndex: Int,
    total: Int,
    storeName: String,
    distanceText: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (total <= 0 || currentIndex < 0) return

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = storeName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
                if (!distanceText.isNullOrBlank()) {
                    Text(
                        text = distanceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "إغلاق التنقل",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = currentIndex > 0,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "السابق",
                    tint = if (currentIndex > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            Text(
                text = "${currentIndex + 1} / $total",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            IconButton(
                onClick = onNext,
                enabled = currentIndex < total - 1,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "التالي",
                    tint = if (currentIndex < total - 1)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}
