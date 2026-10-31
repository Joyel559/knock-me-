package com.joyel.tripguard.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Resolves a (possibly shortened) URL to its final destination. Kept behind an interface so
 * [com.joyel.tripguard.share.ShareIntentParser] tests can fake it without real network calls. */
interface RedirectResolver {
    suspend fun resolve(url: String): String
}

private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
private const val MAX_HOPS = 8
private const val TIMEOUT_MS = 8_000L

/** Follows HTTP redirects manually (headers only, body never read) up to [MAX_HOPS] hops. */
class OkHttpRedirectResolver(private val client: OkHttpClient = OkHttpClient()) : RedirectResolver {
    override suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TIMEOUT_MS) { followRedirects(url) } ?: url
    }

    private fun followRedirects(start: String): String {
        val noFollow = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        var current = start
        repeat(MAX_HOPS) {
            val request = Request.Builder().url(current).header("User-Agent", BROWSER_USER_AGENT).build()
            val next = runCatching {
                noFollow.newCall(request).execute().use { resp ->
                    if (resp.code in 300..399) resp.header("Location") else null
                }
            }.getOrNull() ?: return current
            current = resolveAgainst(current, next)
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
