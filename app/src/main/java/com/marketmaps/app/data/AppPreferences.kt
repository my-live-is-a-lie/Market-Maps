package com.marketmaps.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.marketmaps.app.ui.theme.AccentPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "market_maps_prefs")

enum class MapProvider {
    MAPSFORGE,
    GOOGLE
}

enum class AppThemeMode {
    LIGHT,
    DARK,
    AMOLED
}

class AppPreferences(private val context: Context) {

    private val onboardingDoneKey = booleanPreferencesKey("onboarding_done")
    private val lastLatKey = doublePreferencesKey("last_lat")
    private val lastLonKey = doublePreferencesKey("last_lon")
    private val lastZoomKey = doublePreferencesKey("last_zoom")
    private val areaLabelKey = stringPreferencesKey("area_label")
    private val offlineModeKey = booleanPreferencesKey("offline_mode")
    private val mapFileNameKey = stringPreferencesKey("map_file_name")
    private val mapProviderKey = stringPreferencesKey("map_provider")
    private val rememberFilterKey = booleanPreferencesKey("remember_filter")
    private val filterTypeKey = stringPreferencesKey("filter_type")
    private val filterSubKey = stringPreferencesKey("filter_sub")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val accentKeyKey = stringPreferencesKey("accent_key")

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[onboardingDoneKey] ?: false
    }

    val lastLocation: Flow<Triple<Double, Double, Double>?> = context.dataStore.data.map { prefs ->
        val lat = prefs[lastLatKey]
        val lon = prefs[lastLonKey]
        val zoom = prefs[lastZoomKey] ?: 14.0
        if (lat != null && lon != null) Triple(lat, lon, zoom) else null
    }

    val offlineMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[offlineModeKey] ?: false
    }

    val mapFileName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[mapFileNameKey]
    }

    val mapProvider: Flow<MapProvider> = context.dataStore.data.map { prefs ->
        when (prefs[mapProviderKey]) {
            "GOOGLE" -> MapProvider.GOOGLE
            else -> MapProvider.MAPSFORGE
        }
    }

    val rememberFilter: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[rememberFilterKey] ?: false
    }

    val savedFilterType: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[filterTypeKey] ?: "الكل"
    }

    val savedFilterSub: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[filterSubKey] ?: "الكل"
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[themeModeKey]) {
            "DARK" -> AppThemeMode.DARK
            "AMOLED" -> AppThemeMode.AMOLED
            else -> AppThemeMode.LIGHT
        }
    }

    /** dynamic | teal | blue | ... | custom:#RRGGBB */
    val accentKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[accentKeyKey] ?: AccentPresets.DEFAULT
    }

    suspend fun setOnboardingDone(done: Boolean = true) {
        context.dataStore.edit { it[onboardingDoneKey] = done }
    }

    suspend fun saveLastLocation(lat: Double, lon: Double, zoom: Double = 14.0, areaLabel: String = "") {
        context.dataStore.edit { prefs ->
            prefs[lastLatKey] = lat
            prefs[lastLonKey] = lon
            prefs[lastZoomKey] = zoom
            if (areaLabel.isNotBlank()) prefs[areaLabelKey] = areaLabel
        }
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        context.dataStore.edit { it[offlineModeKey] = enabled }
    }

    suspend fun setMapFileName(name: String) {
        context.dataStore.edit { it[mapFileNameKey] = name }
    }

    suspend fun setMapProvider(provider: MapProvider) {
        context.dataStore.edit { it[mapProviderKey] = provider.name }
    }

    suspend fun setRememberFilter(enabled: Boolean) {
        context.dataStore.edit { it[rememberFilterKey] = enabled }
    }

    suspend fun saveFilter(type: String, sub: String) {
        context.dataStore.edit { prefs ->
            prefs[filterTypeKey] = type
            prefs[filterSubKey] = sub
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setAccentKey(key: String) {
        context.dataStore.edit { it[accentKeyKey] = key }
    }
}
