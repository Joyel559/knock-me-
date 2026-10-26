package com.joyel.tripguard.share

import com.joyel.tripguard.geo.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val COORD_PATTERN = Regex("""(-?\d+\.\d+)[,@](-?\d+\.\d+)""")
private val AT_PATTERN = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
private val QUERY_PATTERN = Regex("""[?&]q=(-?\d+\.\d+),(-?\d+\.\d+)""")
private val GEO_PATTERN = Regex("""geo:(-?\d+\.\d+),(-?\d+\.\d+)""")
private val SHORT_LINK_HOST = "maps.app.goo.gl"

/** Extracts a destination from a shared `geo:` URI or Google Maps text/URL (PRD 6.1). */
class ShareIntentParser(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun parse(text: String?): LatLon? {
        if (text.isNullOrBlank()) return null

        if (SHORT_LINK_HOST in text) {
            val resolved = resolveShortLink(text) ?: return null
            return extractCoordinates(resolved)
        }
        return extractCoordinates(text)
    }

    private fun extractCoordinates(text: String): LatLon? {
        GEO_PATTERN.find(text)?.let { return it.toLatLon() }
        AT_PATTERN.find(text)?.let { return it.toLatLon() }
        QUERY_PATTERN.find(text)?.let { return it.toLatLon() }
        COORD_PATTERN.find(text)?.let { return it.toLatLon() }
        return null
    }

    private fun MatchResult.toLatLon() =
        LatLon(groupValues[1].toDouble(), groupValues[2].toDouble())

    private suspend fun resolveShortLink(text: String): String? = withContext(Dispatchers.IO) {
        val urlMatch = Regex("""https?://\S+""").find(text) ?: return@withContext null
        val noFollowClient = client.newBuilder().followRedirects(false).build()
        runCatching {
            val request = Request.Builder().url(urlMatch.value).build()
            noFollowClient.newCall(request).execute().use { response ->
                response.header("Location") ?: urlMatch.value
            }
        }.getOrNull()
    }
}
