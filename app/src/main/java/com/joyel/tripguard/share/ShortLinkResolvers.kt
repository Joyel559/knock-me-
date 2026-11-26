package com.joyel.tripguard.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** What either short-link resolver can recover: coordinates, a name, or both. Neither field set
 * means "no usable result" (treated as invalid by the caller's race). */
data class ShortLinkResult(val lat: Double? = null, val lon: Double? = null, val name: String? = null)

fun ShortLinkResult.isValid(): Boolean = (lat != null && lon != null) || !name.isNullOrBlank()

interface ShortLinkResolver {
    suspend fun resolve(url: String): ShortLinkResult?
}

private val SHARED_CLIENT: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(3_500, TimeUnit.MILLISECONDS)
        .build()
}

/**
 * Resolver 1: follows the short link's redirect by hand (headers only, body never read), up to 8
 * hops, then runs the same offline URL parsing on the final URL. Part 0's real test showed the
 * Location header for maps.app.goo.gl carries a name (in the `/maps/place/<Name>/` path) but no
 * pin coordinates, and that a second GET of the final page's body does not yield coordinates
 * either (empty og:/meta tags on a mobile UA, only the generic map-viewport center on a desktop
 * UA) — so this resolver deliberately does NOT fetch the final page's body, only its headers.
 */
class LocalRedirectResolver(private val client: OkHttpClient = SHARED_CLIENT) : ShortLinkResolver {
    override suspend fun resolve(url: String): ShortLinkResult? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(3_500) {
            val finalUrl = followRedirects(url)
            val coords = OfflineParse.extractCoordsFromMapsUrl(finalUrl)
            val name = OfflineParse.extractNameFromMapsUrl(finalUrl)
            if (coords == null && name == null) null
            else ShortLinkResult(coords?.lat, coords?.lon, name)
        }
    }

    private fun followRedirects(start: String): String {
        val noFollow = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        var current = start
        repeat(8) {
            val request = Request.Builder().url(current).header("User-Agent", "Mozilla/5.0").build()
            val response = runCatching { noFollow.newCall(request).execute() }.getOrNull() ?: return current
            response.use { resp ->
                if (resp.code !in 300..399) return current
                val location = resp.header("Location") ?: return current
                current = resolveAgainst(current, location)
            }
        }
        return current
    }

    private fun resolveAgainst(base: String, location: String): String =
        if (location.startsWith("http://") || location.startsWith("https://")) {
            location
        } else {
            runCatching { java.net.URI(base).resolve(location).toString() }.getOrDefault(location)
        }
}

@Serializable
private data class UnshortenerResponse(
    val lat: Double? = null,
    val long: Double? = null,
    val name: String? = null,
    val error: String? = null
)

/**
 * Resolver 2: the public mapu.retiolus.net unshortener, confirmed in Part 0's real test to
 * return `{"lat":..,"long":..,"name":..}` for a Google Maps short link in one request, or
 * `{"error":".."}` with HTTP 500 on failure.
 */
class OnlineUnshortenerResolver(private val client: OkHttpClient = SHARED_CLIENT) : ShortLinkResolver {
    override suspend fun resolve(url: String): ShortLinkResult? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(3_000) {
            runCatching {
                val encoded = URLEncoder.encode(url, "UTF-8")
                val request = Request.Builder()
                    .url("${com.joyel.tripguard.BuildConfig.UNSHORTENER_BASE_URL}/unshortener?link=$encoded")
                    .header("User-Agent", "KnockMe/${com.joyel.tripguard.BuildConfig.VERSION_NAME}")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string()
                    val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<UnshortenerResponse>(body)
                    if (parsed.lat == null || parsed.long == null) null
                    else ShortLinkResult(parsed.lat, parsed.long, parsed.name)
                }
            }.getOrNull()
        }
    }
}
