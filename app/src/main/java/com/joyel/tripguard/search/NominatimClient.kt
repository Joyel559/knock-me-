package com.joyel.tripguard.search

import com.joyel.tripguard.geo.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

data class PlaceResult(val label: String, val location: LatLon)

@Serializable
private data class NominatimResult(val lat: String, val lon: String, val display_name: String)

@Serializable
private data class NominatimReverseResult(val display_name: String)

private const val USER_AGENT = "KnockMe/1.0 (personal use, not for distribution)"

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

    suspend fun search(query: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        throttle()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()

        val body = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull() ?: return@withContext emptyList()

        runCatching {
            json.decodeFromString<List<NominatimResult>>(body).map {
                PlaceResult(it.display_name, LatLon(it.lat.toDouble(), it.lon.toDouble()))
            }
        }.getOrDefault(emptyList())
    }

    /** Reverse-geocodes a fix to a readable label. Returns null (never throws) on any failure. */
    suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val key = "%.4f,%.4f".format(lat, lon)
        reverseCache[key]?.let { return@withContext it }
        throttle()
        val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()

        val body = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull() ?: return@withContext null

        runCatching { json.decodeFromString<NominatimReverseResult>(body).display_name }
            .getOrNull()
            ?.also { reverseCache[key] = it }
    }
}
