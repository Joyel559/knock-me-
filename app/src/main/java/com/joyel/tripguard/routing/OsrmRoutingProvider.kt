package com.joyel.tripguard.routing

import com.joyel.tripguard.geo.LatLon
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

class OsrmRoutingProvider(private val client: OkHttpClient = OkHttpClient()) : RoutingProvider {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun route(from: LatLon, to: LatLon): List<LatLon>? = withContext(Dispatchers.IO) {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
            "${from.lon},${from.lat};${to.lon},${to.lat}?overview=full&geometries=geojson"
        val request = Request.Builder().url(url).build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString<OsrmResponse>(body)
                if (parsed.code != "Ok" || parsed.routes.isEmpty()) return@withContext null
                parsed.routes.first().geometry.coordinates.map { LatLon(it[1], it[0]) }
            }
        }.getOrNull()
    }
}
