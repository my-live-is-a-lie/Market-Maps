package com.marketmaps.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.marketmaps.app.ui.theme.AccentPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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

/** جانب القائمة الجانبية */
enum class DrawerSide {
    LEFT,
    RIGHT
}

/** من أين يُفتح السحب من الحافة */
enum class EdgeSwipeSide {
    LEFT,
    RIGHT,
    BOTH
}

/** آخر موقع محفوظ للكاميرا (بدل Triple<Double, Double, Double> غير الواضح) */
data class SavedLocation(val lat: Double, val lon: Double, val zoom: Double)

/** إعدادات الواجهة مجمّعة في كائن واحد (انظر AppPreferences.settings) */
data class AppSettings(
    val offlineMode: Boolean = false,
    val mapFileName: String? = null,
    val mapProvider: MapProvider = MapProvider.MAPSFORGE,
    val showMarkerLabels: Boolean = true,
    val recentSearches: List<String> = emptyList(),
    val recentSearchLimit: Int = 5,
    val drawerSide: DrawerSide = DrawerSide.RIGHT,
    val edgeSwipeEnabled: Boolean = true,
    val edgeSwipeSide: EdgeSwipeSide = EdgeSwipeSide.BOTH,
    val edgeSwipeSensitivity: Float = 0.55f,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val accentKey: String = AccentPresets.DEFAULT
)

/** قيمة فلتر «الكل» (كانت مكررة كنص في عشرات المواضع) */
const val FILTER_ALL = "الكل"

private const val RECENT_SEPARATOR = "||"

/** قراءة enum محفوظ كنص؛ أي قيمة غير معروفة (من إصدار أقدم/أحدث) تعود للافتراضي بدل الانهيار */
private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: default

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
    private val recentSearchesKey = stringPreferencesKey("recent_searches")
    private val recentSearchLimitKey = intPreferencesKey("recent_search_limit")
    private val showMarkerLabelsKey = booleanPreferencesKey("show_marker_labels")
    private val drawerSideKey = stringPreferencesKey("drawer_side")
    private val edgeSwipeEnabledKey = booleanPreferencesKey("edge_swipe_enabled")
    private val edgeSwipeSideKey = stringPreferencesKey("edge_swipe_side")
    private val edgeSwipeSensitivityKey = doublePreferencesKey("edge_swipe_sensitivity")

    // ---- قراءة القيم من Preferences (مصدر واحد تستخدمه الـ Flows المنفردة و AppSettings) ----

    private fun Preferences.readOfflineMode(): Boolean = this[offlineModeKey] ?: false
    private fun Preferences.readMapFileName(): String? = this[mapFileNameKey]
    private fun Preferences.readMapProvider(): MapProvider = this[mapProviderKey].toEnumOr(MapProvider.MAPSFORGE)
    private fun Preferences.readThemeMode(): AppThemeMode = this[themeModeKey].toEnumOr(AppThemeMode.LIGHT)
    private fun Preferences.readAccentKey(): String = this[accentKeyKey] ?: AccentPresets.DEFAULT
    private fun Preferences.readRecentSearches(): List<String> {
        val raw = this[recentSearchesKey] ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(RECENT_SEPARATOR).filter { it.isNotBlank() }
    }
    private fun Preferences.readRecentSearchLimit(): Int = (this[recentSearchLimitKey] ?: 5).coerceIn(0, 8)
    private fun Preferences.readShowMarkerLabels(): Boolean = this[showMarkerLabelsKey] ?: true
    private fun Preferences.readDrawerSide(): DrawerSide = this[drawerSideKey].toEnumOr(DrawerSide.RIGHT)
    private fun Preferences.readEdgeSwipeEnabled(): Boolean = this[edgeSwipeEnabledKey] ?: true
    private fun Preferences.readEdgeSwipeSide(): EdgeSwipeSide = this[edgeSwipeSideKey].toEnumOr(EdgeSwipeSide.BOTH)
    private fun Preferences.readEdgeSwipeSensitivity(): Float =
        (this[edgeSwipeSensitivityKey] ?: 0.55).toFloat().coerceIn(0.05f, 1f)

    /**
     * كل كتابة في DataStore تجعل كل Flow مشتق من dataStore.data يُصدر من جديد.
     * distinctUntilChanged يمنع وصول قيمة لم تتغير إلى الواجهة.
     */
    private fun <T> pref(read: (Preferences) -> T): Flow<T> =
        context.dataStore.data.map(read).distinctUntilChanged()

    /**
     * كل إعدادات الواجهة في Flow واحد: MapScreen كان يراقب 10 Flows منفصلة،
     * كل منها يعيد الحساب ويُطلق collectAsState مع أي كتابة في DataStore.
     */
    val settings: Flow<AppSettings> = pref { p ->
        AppSettings(
            offlineMode = p.readOfflineMode(),
            mapFileName = p.readMapFileName(),
            mapProvider = p.readMapProvider(),
            showMarkerLabels = p.readShowMarkerLabels(),
            recentSearches = p.readRecentSearches(),
            recentSearchLimit = p.readRecentSearchLimit(),
            drawerSide = p.readDrawerSide(),
            edgeSwipeEnabled = p.readEdgeSwipeEnabled(),
            edgeSwipeSide = p.readEdgeSwipeSide(),
            edgeSwipeSensitivity = p.readEdgeSwipeSensitivity(),
            themeMode = p.readThemeMode(),
            accentKey = p.readAccentKey()
        )
    }

    val onboardingDone: Flow<Boolean> = pref { it[onboardingDoneKey] ?: false }

    val lastLocation: Flow<SavedLocation?> = pref { prefs ->
        val lat = prefs[lastLatKey] ?: return@pref null
        val lon = prefs[lastLonKey] ?: return@pref null
        SavedLocation(lat, lon, prefs[lastZoomKey] ?: 14.0)
    }

    val areaLabel: Flow<String> = pref { it[areaLabelKey] ?: "" }
    val offlineMode: Flow<Boolean> = pref { it.readOfflineMode() }
    val mapFileName: Flow<String?> = pref { it.readMapFileName() }
    val mapProvider: Flow<MapProvider> = pref { it.readMapProvider() }
    val rememberFilter: Flow<Boolean> = pref { it[rememberFilterKey] ?: false }
    val savedFilterType: Flow<String> = pref { it[filterTypeKey] ?: FILTER_ALL }
    val savedFilterSub: Flow<String> = pref { it[filterSubKey] ?: FILTER_ALL }
    val themeMode: Flow<AppThemeMode> = pref { it.readThemeMode() }
    val accentKey: Flow<String> = pref { it.readAccentKey() }
    val recentSearches: Flow<List<String>> = pref { it.readRecentSearches() }
    val recentSearchLimit: Flow<Int> = pref { it.readRecentSearchLimit() }

    /** إظهار أسماء المواقع بجانب الأيقونات على الخريطة */
    val showMarkerLabels: Flow<Boolean> = pref { it.readShowMarkerLabels() }
    val drawerSide: Flow<DrawerSide> = pref { it.readDrawerSide() }
    val edgeSwipeEnabled: Flow<Boolean> = pref { it.readEdgeSwipeEnabled() }
    val edgeSwipeSide: Flow<EdgeSwipeSide> = pref { it.readEdgeSwipeSide() }
    val edgeSwipeSensitivity: Flow<Float> = pref { it.readEdgeSwipeSensitivity() }

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

    suspend fun setRecentSearchLimit(limit: Int) {
        context.dataStore.edit { it[recentSearchLimitKey] = limit.coerceIn(0, 8) }
    }

    suspend fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        context.dataStore.edit { prefs ->
            val limit = (prefs[recentSearchLimitKey] ?: 5).coerceIn(0, 8)
            if (limit == 0) return@edit
            val current = (prefs[recentSearchesKey] ?: "")
                .split(RECENT_SEPARATOR)
                .filter { it.isNotBlank() && !it.equals(q, ignoreCase = true) }
            val updated = (listOf(q) + current).take(8)
            prefs[recentSearchesKey] = updated.joinToString(RECENT_SEPARATOR)
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { it[recentSearchesKey] = "" }
    }

    suspend fun setShowMarkerLabels(show: Boolean) {
        context.dataStore.edit { it[showMarkerLabelsKey] = show }
    }

    suspend fun setDrawerSide(side: DrawerSide) {
        context.dataStore.edit { it[drawerSideKey] = side.name }
    }

    suspend fun toggleDrawerSide() {
        context.dataStore.edit { prefs ->
            val current = prefs[drawerSideKey]
            prefs[drawerSideKey] = if (current == DrawerSide.LEFT.name) DrawerSide.RIGHT.name else DrawerSide.LEFT.name
        }
    }

    suspend fun setEdgeSwipeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[edgeSwipeEnabledKey] = enabled }
    }

    suspend fun setEdgeSwipeSide(side: EdgeSwipeSide) {
        context.dataStore.edit { it[edgeSwipeSideKey] = side.name }
    }

    suspend fun setEdgeSwipeSensitivity(value: Float) {
        context.dataStore.edit { it[edgeSwipeSensitivityKey] = value.coerceIn(0.05f, 1f).toDouble() }
    }
}

