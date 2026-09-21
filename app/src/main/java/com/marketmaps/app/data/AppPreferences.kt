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

/**
 * حفظ إعدادات التطبيق: هل اكتمل أول استخدام، وآخر موقع للخريطة.
 */
class AppPreferences(private val context: Context) {

    private val onboardingDoneKey = booleanPreferencesKey("onboarding_done")
    private val lastLatKey = doublePreferencesKey("last_lat")
    private val lastLonKey = doublePreferencesKey("last_lon")
    private val lastZoomKey = doublePreferencesKey("last_zoom")
    private val areaLabelKey = stringPreferencesKey("area_label")

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[onboardingDoneKey] ?: false
    }

    val lastLocation: Flow<Triple<Double, Double, Double>?> = context.dataStore.data.map { prefs ->
        val lat = prefs[lastLatKey]
        val lon = prefs[lastLonKey]
        val zoom = prefs[lastZoomKey] ?: 14.0
        if (lat != null && lon != null) Triple(lat, lon, zoom) else null
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
}
