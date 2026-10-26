package com.joyel.tripguard.trip

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.tripDataStore by preferencesDataStore(name = "tripguard_trip")
private val ACTIVE_TRIP_KEY = stringPreferencesKey("active_trip_json")

private val json = Json { ignoreUnknownKeys = true }

/** Persists the single active [Trip] (or null) so it survives process death and reboot. */
class TripRepository(private val context: Context) {

    fun observeActiveTrip(): Flow<Trip?> =
        context.tripDataStore.data.map { prefs ->
            prefs[ACTIVE_TRIP_KEY]?.let { runCatching { json.decodeFromString<Trip>(it) }.getOrNull() }
        }

    suspend fun getActiveTrip(): Trip? = observeActiveTrip().first()

    suspend fun saveActiveTrip(trip: Trip?) {
        context.tripDataStore.edit { prefs ->
            if (trip == null) {
                prefs.remove(ACTIVE_TRIP_KEY)
            } else {
                prefs[ACTIVE_TRIP_KEY] = json.encodeToString(trip)
            }
        }
    }
}
