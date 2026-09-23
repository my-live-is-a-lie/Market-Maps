package com.marketmaps.app.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF7F7F7)
val LightOnBackground = Color(0xFF1A1A1A)
val LightOnSurface = Color(0xFF1A1A1A)

val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkOnBackground = Color(0xFFFFFFFF)
val DarkOnSurface = Color(0xFFFFFFFF)

val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF000000)
val AmoledOnBackground = Color(0xFFFFFFFF)
val AmoledOnSurface = Color(0xFFFFFFFF)

/** ألوان التمييز الجاهزة */
object AccentPresets {
    data class Preset(val key: String, val label: String, val color: Color)

    val list = listOf(
        Preset("teal", "أخضر مزرق", Color(0xFF00897B)),
        Preset("blue", "أزرق", Color(0xFF1E88E5)),
        Preset("green", "أخضر", Color(0xFF43A047)),
        Preset("orange", "برتقالي", Color(0xFFFB8C00)),
        Preset("red", "أحمر", Color(0xFFE53935)),
        Preset("purple", "بنفسجي", Color(0xFF8E24AA)),
        Preset("pink", "وردي", Color(0xFFEC407A)),
        Preset("indigo", "نيلي", Color(0xFF3949AB))
    )

    const val DYNAMIC = "dynamic"
    const val DEFAULT = "teal"

    fun colorForKey(key: String): Color? {
        if (key.startsWith("custom:")) {
            val hex = key.removePrefix("custom:")
            return try {
                Color(android.graphics.Color.parseColor(hex))
            } catch (_: Exception) {
                null
            }
        }
        return list.find { it.key == key }?.color
    }

    fun labelForKey(key: String): String {
        return when {
            key == DYNAMIC -> "ديناميكي (من النظام)"
            key.startsWith("custom:") -> "مخصص (${key.removePrefix("custom:")})"
            else -> list.find { it.key == key }?.label ?: "أخضر مزرق"
        }
    }
}
