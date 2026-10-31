package com.joyel.tripguard.trip

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reliabilityDataStore by preferencesDataStore(name = "tripguard_reliability")

private val KEY_MAX_RELIABILITY = booleanPreferencesKey("max_reliability_enabled")
private val KEY_INTERRUPTION_DETECTED = booleanPreferencesKey("interruption_detected")
private val KEY_CARD_DISMISSED_UNTIL = longPreferencesKey("card_dismissed_until_ms")

data class ReliabilityState(
    val maxReliabilityEnabled: Boolean = false,
    val interruptionDetected: Boolean = false,
    val cardDismissedUntilMs: Long = 0L
)

private const val DISMISS_DAYS_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Lean mode (v1.5) vs Max reliability setting, plus the "your trip was interrupted" sticky flag
 * that drives the one calm Home card (never shown on first launch, only after a real interruption).
 */
class ReliabilityRepository(private val context: Context) {

    fun observe(): Flow<ReliabilityState> = context.reliabilityDataStore.data.map { prefs ->
        ReliabilityState(
            maxReliabilityEnabled = prefs[KEY_MAX_RELIABILITY] ?: false,
            interruptionDetected = prefs[KEY_INTERRUPTION_DETECTED] ?: false,
            cardDismissedUntilMs = prefs[KEY_CARD_DISMISSED_UNTIL] ?: 0L
        )
    }

    suspend fun get(): ReliabilityState = observe().first()

    suspend fun setMaxReliability(enabled: Boolean) {
        context.reliabilityDataStore.edit { it[KEY_MAX_RELIABILITY] = enabled }
    }

    suspend fun markInterruptionDetected() {
        context.reliabilityDataStore.edit { it[KEY_INTERRUPTION_DETECTED] = true }
    }

    suspend fun dismissCardForSevenDays() {
        context.reliabilityDataStore.edit {
            it[KEY_CARD_DISMISSED_UNTIL] = System.currentTimeMillis() + DISMISS_DAYS_MS
        }
    }

    /** Whether the Home "turn on Max reliability" card should show right now. */
    fun shouldShowCard(state: ReliabilityState, nowMs: Long = System.currentTimeMillis()): Boolean =
        state.interruptionDetected && !state.maxReliabilityEnabled && nowMs > state.cardDismissedUntilMs
}
