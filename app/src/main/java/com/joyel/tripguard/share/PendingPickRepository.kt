package com.joyel.tripguard.share

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.pendingPickDataStore by preferencesDataStore(name = "tripguard_pending_pick")
private val KEY_PENDING_PICK = stringPreferencesKey("pending_pick_json")
private val json = Json { ignoreUnknownKeys = true }

/** Persists the single in-flight "Choose in Google Maps" pick (+ the Trip Setup draft) across
 * process death, with a 30-minute expiry so a pick nobody acted on doesn't resurface forever. */
class PendingPickRepository(private val context: Context) {

    /** Read without clearing. Auto-clears and returns null if the pick has expired. */
    suspend fun peek(): PendingPick? {
        val raw = context.pendingPickDataStore.data.first()[KEY_PENDING_PICK] ?: return null
        val pick = runCatching { json.decodeFromString<PendingPick>(raw) }.getOrNull() ?: return null
        if (pick.isExpired(System.currentTimeMillis())) {
            clear()
            return null
        }
        return pick
    }

    /** Read-and-clear in one call — the caller is taking ownership of applying it. */
    suspend fun consume(): PendingPick? {
        val pick = peek()
        clear()
        return pick
    }

    suspend fun set(pick: PendingPick) {
        context.pendingPickDataStore.edit { it[KEY_PENDING_PICK] = json.encodeToString(pick) }
    }

    suspend fun clear() {
        context.pendingPickDataStore.edit { it.remove(KEY_PENDING_PICK) }
    }
}
