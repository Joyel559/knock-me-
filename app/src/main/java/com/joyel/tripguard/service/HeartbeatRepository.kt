package com.joyel.tripguard.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.heartbeatDataStore by preferencesDataStore(name = "tripguard_heartbeat")
private val KEY_LAST_HEARTBEAT_ELAPSED = longPreferencesKey("last_heartbeat_elapsed_ms")
private val KEY_LAST_HEARTBEAT_WALL = longPreferencesKey("last_heartbeat_wall_ms")
private val KEY_LAST_RING_ELAPSED = longPreferencesKey("last_watchdog_ring_elapsed_ms")
private const val MIN_WRITE_INTERVAL_MS = 30_000L

/** The watchdog's dead-man-check state: when the trip last proved it was alive, and when the
 * watchdog alert last rang (for its own 10-minute cooldown). */
class HeartbeatRepository(private val context: Context) {
    private var lastWriteAtElapsed = 0L

    /** Writes at most once per 30s, so the trip's fast location loop doesn't hit DataStore every fix. */
    suspend fun recordHeartbeat(nowElapsedMs: Long) {
        if (nowElapsedMs - lastWriteAtElapsed < MIN_WRITE_INTERVAL_MS) return
        lastWriteAtElapsed = nowElapsedMs
        context.heartbeatDataStore.edit { prefs ->
            prefs[KEY_LAST_HEARTBEAT_ELAPSED] = nowElapsedMs
            prefs[KEY_LAST_HEARTBEAT_WALL] = System.currentTimeMillis()
        }
    }

    suspend fun getLastHeartbeatElapsedMs(): Long? =
        context.heartbeatDataStore.data.first()[KEY_LAST_HEARTBEAT_ELAPSED]

    suspend fun getLastRingElapsedMs(): Long? =
        context.heartbeatDataStore.data.first()[KEY_LAST_RING_ELAPSED]

    suspend fun recordRing(nowElapsedMs: Long) {
        context.heartbeatDataStore.edit { it[KEY_LAST_RING_ELAPSED] = nowElapsedMs }
    }
}
