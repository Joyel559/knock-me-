package com.joyel.tripguard.ui.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.onboardingDataStore by preferencesDataStore(name = "knockme_onboarding")
private val COMPLETE = booleanPreferencesKey("onboarding_complete")
private val SKIPPED = stringPreferencesKey("skipped_steps")
private val DISCLOSURE_SEEN = booleanPreferencesKey("location_disclosure_seen")
private val BACKGROUND_DISCLOSURE_SEEN = booleanPreferencesKey("background_location_disclosure_seen")

/** Tracks onboarding progress so re-opening the app resumes at the first missing step (PRD N3). */
class OnboardingRepository(private val context: Context) {
    suspend fun isComplete(): Boolean = context.onboardingDataStore.data.first()[COMPLETE] ?: false

    suspend fun setComplete() {
        context.onboardingDataStore.edit { it[COMPLETE] = true }
    }

    suspend fun skippedSteps(): Set<String> =
        (context.onboardingDataStore.data.first()[SKIPPED] ?: "")
            .split(",").filter { it.isNotBlank() }.toSet()

    /** Play policy: a prominent disclosure must be shown once, before the first system location
     * permission dialog, as its own screen rather than folded into the permission prompt. */
    suspend fun isDisclosureSeen(): Boolean = context.onboardingDataStore.data.first()[DISCLOSURE_SEEN] ?: false

    suspend fun setDisclosureSeen() {
        context.onboardingDataStore.edit { it[DISCLOSURE_SEEN] = true }
    }

    suspend fun isBackgroundDisclosureSeen(): Boolean =
        context.onboardingDataStore.data.first()[BACKGROUND_DISCLOSURE_SEEN] ?: false

    suspend fun setBackgroundDisclosureSeen() {
        context.onboardingDataStore.edit { it[BACKGROUND_DISCLOSURE_SEEN] = true }
    }

    suspend fun skip(stepId: String) {
        context.onboardingDataStore.edit { prefs ->
            val current = (prefs[SKIPPED] ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()
            current.add(stepId)
            prefs[SKIPPED] = current.joinToString(",")
        }
    }
}
