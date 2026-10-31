package com.joyel.tripguard.trip

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "tripguard_settings")

data class AppSettings(
    val defaultRadiusMeters: Int = 3000,
    val defaultMode: Mode = Mode.CAB,
    val maxVolumeOnAlarm: Boolean = true,
    val vibrateOnAlarm: Boolean = true,
    val routeThresholdM: Int = 500,
    val routeConsecutiveCount: Int = 3,
    val defaultFinalAlarmEnabled: Boolean = false,
    val defaultFinalAlarmMeters: Int = DEFAULT_FINAL_ALARM_RADIUS_M,
    val searchCountryCode: String? = null
) {
    /** km rounded for display only; the stored value is always meters. */
    val defaultRadiusKm: Int get() = (defaultRadiusMeters / 1000.0).let { if (it < 1) 1 else it.toInt() }
}

private val KEY_RADIUS_M = intPreferencesKey("default_radius_meters")
private val KEY_MODE = stringPreferencesKey("default_mode")
private val KEY_MAX_VOLUME = booleanPreferencesKey("max_volume_on_alarm")
private val KEY_VIBRATE = booleanPreferencesKey("vibrate_on_alarm")
private val KEY_ROUTE_THRESHOLD = intPreferencesKey("route_threshold_m")
private val KEY_ROUTE_CONSECUTIVE = intPreferencesKey("route_consecutive_count")
private val KEY_FINAL_ENABLED = booleanPreferencesKey("default_final_alarm_enabled")
private val KEY_FINAL_METERS = intPreferencesKey("default_final_alarm_meters")
private val KEY_SEARCH_COUNTRY = stringPreferencesKey("search_country_code")

class SettingsRepository(private val context: Context) {

    fun observe(): Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            defaultRadiusMeters = prefs[KEY_RADIUS_M] ?: 3000,
            defaultMode = prefs[KEY_MODE]?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.CAB,
            maxVolumeOnAlarm = prefs[KEY_MAX_VOLUME] ?: true,
            vibrateOnAlarm = prefs[KEY_VIBRATE] ?: true,
            routeThresholdM = prefs[KEY_ROUTE_THRESHOLD] ?: 500,
            routeConsecutiveCount = prefs[KEY_ROUTE_CONSECUTIVE] ?: 3,
            defaultFinalAlarmEnabled = prefs[KEY_FINAL_ENABLED] ?: false,
            defaultFinalAlarmMeters = prefs[KEY_FINAL_METERS] ?: DEFAULT_FINAL_ALARM_RADIUS_M,
            searchCountryCode = prefs[KEY_SEARCH_COUNTRY]
        )
    }

    suspend fun get(): AppSettings = observe().first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(get())
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_RADIUS_M] = next.defaultRadiusMeters
            prefs[KEY_MODE] = next.defaultMode.name
            prefs[KEY_MAX_VOLUME] = next.maxVolumeOnAlarm
            prefs[KEY_VIBRATE] = next.vibrateOnAlarm
            prefs[KEY_ROUTE_THRESHOLD] = next.routeThresholdM
            prefs[KEY_ROUTE_CONSECUTIVE] = next.routeConsecutiveCount
            prefs[KEY_FINAL_ENABLED] = next.defaultFinalAlarmEnabled
            prefs[KEY_FINAL_METERS] = next.defaultFinalAlarmMeters
            next.searchCountryCode?.let { prefs[KEY_SEARCH_COUNTRY] = it }
        }
    }
}
