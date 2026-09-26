package com.marketmaps.app.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * قسم أيقونة التطبيق المتكيفة مع ألوان خلفية النظام (Material You / Themed Icons).
 * التحكم الفعلي يكون من إعدادات النظام على أندرويد 12 فأعلى.
 */
@Composable
fun ThemedIconSettingsSection() {
    val context = LocalContext.current

    Text(text = "أيقونة التطبيق", style = MaterialTheme.typography.titleLarge)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "الأيقونات المتكيفة",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "يدعم التطبيق الأيقونات المتكيفة مع ألوان خلفية النظام (مثل Material You). عند تفعيلها من إعدادات الهاتف، تتغير أيقونة التطبيق تلقائياً لتناسب خلفية الشاشة الرئيسية.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "هذه الميزة متوفرة على أندرويد 12 فأعلى، ويتحكم فيها النظام وليس التطبيق.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    val intents = listOf(
                        Intent("android.settings.WALLPAPER_SETTINGS"),
                        Intent(Settings.ACTION_DISPLAY_SETTINGS),
                        Intent(Settings.ACTION_SETTINGS)
                    )
                    var opened = false
                    for (intent in intents) {
                        try {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            opened = true
                            break
                        } catch (_: Exception) {
                        }
                    }
                    if (!opened) {
                        Toast.makeText(
                            context,
                            "افتح إعدادات الهاتف ← الخلفية والنمط ← أيقونات ذات طابع",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("فتح إعدادات النظام")
            }
        }
    }
}
