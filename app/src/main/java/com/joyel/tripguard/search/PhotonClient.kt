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

private val BASE_URL get() = com.joyel.tripguard.BuildConfig.PHOTON_BASE_URL

@Serializable
private data class PhotonProperties(
    val name: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val district: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null
)

@Serializable
private data class PhotonGeometry(val coordinates: List<Double>)

@Serializable
private data class PhotonFeature(val properties: PhotonProperties, val geometry: PhotonGeometry)

@Serializable
private data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

@Serializable
private data class PhotonReverseResponse(val features: List<PhotonFeature> = emptyList())

/** Photon (komoot.io) geocoder — free, no API key, no rate limit stated but used politely. */
class PhotonClient(private val client: OkHttpClient = OkHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, near: LatLon? = null, limit: Int = 8): List<PlaceResult> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val bias = near?.let { "&lat=${it.lat}&lon=${it.lon}" }.orEmpty()
            val url = "$BASE_URL/api/?q=$encoded&limit=$limit&lang=en$bias"
            val request = Request.Builder().url(url).header("User-Agent", KNOCKME_USER_AGENT).build()

            val body = runCatching {
                executeWithBackoff(client, request)?.use { r -> if (r.isSuccessful) r.body.string() else null }
            }.getOrNull() ?: return@withContext emptyList()

            runCatching {
                json.decodeFromString<PhotonResponse>(body).features.mapNotNull { it.toPlaceResult() }
            }.getOrDefault(emptyList())
        }

    suspend fun reverse(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/reverse?lat=$lat&lon=$lon"
        val request = Request.Builder().url(url).header("User-Agent", KNOCKME_USER_AGENT).build()
        val body = runCatching {
            executeWithBackoff(client, request)?.use { r -> if (r.isSuccessful) r.body.string() else null }
        }.getOrNull() ?: return@withContext null
        runCatching {
            json.decodeFromString<PhotonReverseResponse>(body).features.firstOrNull()?.toPlaceResult()?.label
        }.getOrNull()
    }

    private fun PhotonFeature.toPlaceResult(): PlaceResult? {
        val coords = geometry.coordinates
        if (coords.size < 2) return null
        val lon = coords[0]
        val lat = coords[1]
        val p = properties
        val parts = mutableListOf<String>()
        val namePart = listOfNotNull(p.name, p.housenumber?.let { h -> p.street?.let { s -> "$h $s" } ?: p.street })
            .firstOrNull() ?: p.street
        namePart?.let { parts += it }
        p.district?.takeIf { it != namePart }?.let { parts += it }
        p.city?.takeIf { it != namePart }?.let { parts += it }
        p.state?.let { parts += it }
        if (parts.isEmpty()) return null
        val label = parts.distinct().joinToString(", ")
        return PlaceResult(label, LatLon(lat, lon))
    }
}
