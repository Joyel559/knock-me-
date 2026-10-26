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
    val defaultRadiusKm: Int = 3,
    val defaultMode: Mode = Mode.CAB,
    val maxVolumeOnAlarm: Boolean = true,
    val vibrateOnAlarm: Boolean = true,
    val routeThresholdM: Int = 500,
    val routeConsecutiveCount: Int = 3
)

private val KEY_RADIUS = intPreferencesKey("default_radius_km")
private val KEY_MODE = stringPreferencesKey("default_mode")
private val KEY_MAX_VOLUME = booleanPreferencesKey("max_volume_on_alarm")
private val KEY_VIBRATE = booleanPreferencesKey("vibrate_on_alarm")
private val KEY_ROUTE_THRESHOLD = intPreferencesKey("route_threshold_m")
private val KEY_ROUTE_CONSECUTIVE = intPreferencesKey("route_consecutive_count")

class SettingsRepository(private val context: Context) {

    fun observe(): Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            defaultRadiusKm = prefs[KEY_RADIUS] ?: 3,
            defaultMode = prefs[KEY_MODE]?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.CAB,
            maxVolumeOnAlarm = prefs[KEY_MAX_VOLUME] ?: true,
            vibrateOnAlarm = prefs[KEY_VIBRATE] ?: true,
            routeThresholdM = prefs[KEY_ROUTE_THRESHOLD] ?: 500,
            routeConsecutiveCount = prefs[KEY_ROUTE_CONSECUTIVE] ?: 3
        )
    }

    suspend fun get(): AppSettings = observe().first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(get())
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_RADIUS] = next.defaultRadiusKm
            prefs[KEY_MODE] = next.defaultMode.name
            prefs[KEY_MAX_VOLUME] = next.maxVolumeOnAlarm
            prefs[KEY_VIBRATE] = next.vibrateOnAlarm
            prefs[KEY_ROUTE_THRESHOLD] = next.routeThresholdM
            prefs[KEY_ROUTE_CONSECUTIVE] = next.routeConsecutiveCount
        }
    }
}
