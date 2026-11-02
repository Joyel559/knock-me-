package com.joyel.tripguard.net

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** One User-Agent for every outgoing request, identifying the app and a contact per fair-use
 * etiquette for the free public Nominatim/Photon/OSRM servers. */
val KNOCKME_USER_AGENT: String by lazy {
    "KnockMe/${com.joyel.tripguard.BuildConfig.VERSION_NAME} (${com.joyel.tripguard.BuildConfig.CONTACT_EMAIL})"
}

/**
 * Executes [request], retrying with exponential backoff (500ms, 1s, 2s) on HTTP 429/5xx —
 * the Nominatim/Photon/OSRM free public servers rate-limit and occasionally 5xx under load.
 * Returns null (never throws) if every attempt fails or the final response still isn't usable.
 */
suspend fun executeWithBackoff(client: OkHttpClient, request: Request, maxAttempts: Int = 3): Response? {
    var delayMs = 500L
    repeat(maxAttempts) { attempt ->
        val response = runCatching { client.newCall(request).execute() }.getOrNull() ?: return null
        if (response.code == 429 || response.code in 500..599) {
            response.close()
            if (attempt == maxAttempts - 1) return null
            delay(delayMs)
            delayMs *= 2
        } else {
            return response
        }
    }
    return null
}
