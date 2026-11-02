package com.joyel.tripguard.search

import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.net.KNOCKME_USER_AGENT
import com.joyel.tripguard.net.executeWithBackoff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

@Serializable
private data class NominatimResult(val lat: String, val lon: String, val display_name: String)

@Serializable
private data class NominatimAddress(val country_code: String? = null)

@Serializable
private data class NominatimReverseResult(val display_name: String, val address: NominatimAddress? = null)

private val BASE_URL get() = com.joyel.tripguard.BuildConfig.NOMINATIM_BASE_URL

/** Nominatim search + reverse geocode, max 1 request/second total across both (PRD 4). */
class NominatimClient(private val client: OkHttpClient = OkHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true }
    private var lastRequestAtMs = 0L
    private val reverseCache = mutableMapOf<String, String>()

    private suspend fun throttle() {
        val waitMs = 1000L - (System.currentTimeMillis() - lastRequestAtMs)
        if (waitMs > 0) kotlinx.coroutines.delay(waitMs)
        lastRequestAtMs = System.currentTimeMillis()
    }

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        throttle()
        val request = Request.Builder().url(url).header("User-Agent", KNOCKME_USER_AGENT).build()
        runCatching {
            executeWithBackoff(client, request)?.use { response ->
                if (!response.isSuccessful) null else response.body.string()
            }
        }.getOrNull()
    }

    private suspend fun rawSearch(url: String): List<PlaceResult> {
        val body = fetch(url) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<NominatimResult>>(body).map {
                PlaceResult(it.display_name, LatLon(it.lat.toDouble(), it.lon.toDouble()))
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Searches Nominatim, optionally restricted to [countryCode] (ISO 3166-1 alpha-2, lowercase).
     * If that first query returns nothing and [near] is known, retries with a ~100km viewbox
     * around it and no country restriction, so a search near a border still finds something.
     */
    suspend fun search(query: String, countryCode: String? = null, near: LatLon? = null): List<PlaceResult> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val cc = countryCode?.let { "&countrycodes=$it" }.orEmpty()
        val first = rawSearch("$BASE_URL/search?q=$encoded&format=json&limit=8$cc")
        if (first.isNotEmpty() || near == null) return first

        // ~100km box: 1 degree latitude ~111km, longitude scaled by cos(lat).
        val dLat = 100.0 / 111.0
        val dLon = dLat / kotlin.math.max(0.1, kotlin.math.cos(Math.toRadians(near.lat)))
        val viewbox = "${near.lon - dLon},${near.lat + dLat},${near.lon + dLon},${near.lat - dLat}"
        return rawSearch("$BASE_URL/search?q=$encoded&format=json&limit=8&viewbox=$viewbox&bounded=0")
    }

    /** Reverse-geocodes a fix to a readable label. Returns null (never throws) on any failure. */
    suspend fun reverseGeocode(lat: Double, lon: Double): String? {
        val key = "%.4f,%.4f".format(lat, lon)
        reverseCache[key]?.let { return it }
        val body = fetch("$BASE_URL/reverse?format=json&lat=$lat&lon=$lon") ?: return null
        return runCatching { json.decodeFromString<NominatimReverseResult>(body).display_name }
            .getOrNull()
            ?.also { reverseCache[key] = it }
    }

    /** Country code (ISO 3166-1 alpha-2, lowercase) for a fix, used to bias search. Null on failure. */
    suspend fun reverseCountryCode(lat: Double, lon: Double): String? {
        val body = fetch("$BASE_URL/reverse?format=json&addressdetails=1&lat=$lat&lon=$lon") ?: return null
        return runCatching { json.decodeFromString<NominatimReverseResult>(body).address?.country_code }.getOrNull()
    }
}
