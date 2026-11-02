package com.joyel.tripguard.routing

import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.net.KNOCKME_USER_AGENT
import com.joyel.tripguard.net.executeWithBackoff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class OsrmResponse(val code: String, val routes: List<OsrmRoute> = emptyList())

@Serializable
private data class OsrmRoute(val geometry: OsrmGeometry)

@Serializable
private data class OsrmGeometry(val coordinates: List<List<Double>>)

/** Keyless public OSRM demo routing. Falls back to null (arrival alarm still works) if
 * unavailable — see docs/DECISIONS.md and store/release-checklist.md for the fair-use risk. */
class OsrmRoutingProvider(private val client: OkHttpClient = OkHttpClient()) : RoutingProvider {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun route(from: LatLon, to: LatLon): List<LatLon>? = withContext(Dispatchers.IO) {
        val baseUrl = com.joyel.tripguard.BuildConfig.OSRM_BASE_URL
        val url = "$baseUrl/route/v1/driving/" +
            "${from.lon},${from.lat};${to.lon},${to.lat}?overview=full&geometries=geojson"
        val request = Request.Builder().url(url).header("User-Agent", KNOCKME_USER_AGENT).build()

        runCatching {
            executeWithBackoff(client, request)?.use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body.string()
                val parsed = json.decodeFromString<OsrmResponse>(body)
                if (parsed.code != "Ok" || parsed.routes.isEmpty()) return@withContext null
                parsed.routes.first().geometry.coordinates.map { LatLon(it[1], it[0]) }
            }
        }.getOrNull()
    }
}
