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

/** Tracks onboarding progress so re-opening the app resumes at the first missing step (PRD N3). */
class OnboardingRepository(private val context: Context) {
    suspend fun isComplete(): Boolean = context.onboardingDataStore.data.first()[COMPLETE] ?: false

    suspend fun setComplete() {
        context.onboardingDataStore.edit { it[COMPLETE] = true }
    }

    suspend fun skippedSteps(): Set<String> =
        (context.onboardingDataStore.data.first()[SKIPPED] ?: "")
            .split(",").filter { it.isNotBlank() }.toSet()

    suspend fun skip(stepId: String) {
        context.onboardingDataStore.edit { prefs ->
            val current = (prefs[SKIPPED] ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()
            current.add(stepId)
            prefs[SKIPPED] = current.joinToString(",")
        }
    }
}
