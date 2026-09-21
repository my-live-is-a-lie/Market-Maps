package com.marketmaps.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "market_maps_prefs")

class AppPreferences(private val context: Context) {

    private val onboardingDoneKey = booleanPreferencesKey("onboarding_done")
    private val lastLatKey = doublePreferencesKey("last_lat")
    private val lastLonKey = doublePreferencesKey("last_lon")
    private val lastZoomKey = doublePreferencesKey("last_zoom")
    private val areaLabelKey = stringPreferencesKey("area_label")
    private val offlineModeKey = booleanPreferencesKey("offline_mode")
    private val mapFileNameKey = stringPreferencesKey("map_file_name")

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
}
