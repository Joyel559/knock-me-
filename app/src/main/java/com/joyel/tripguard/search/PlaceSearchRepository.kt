package com.joyel.tripguard.search

import android.content.Context
import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.history.HistoryRepository
import com.joyel.tripguard.history.PlaceCache
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PROVIDER_TIMEOUT_MS = 6_000L
private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
private const val LRU_CAPACITY = 50

@Serializable
private data class CachedResult(val label: String, val lat: Double, val lon: Double)

/**
 * No-API-key place search: local saved/recent places first, then Photon, then Nominatim as
 * backup, run in parallel with a per-provider timeout so one slow/dead provider never blocks
 * the others. Results are merged, de-duplicated (same name within 100m) and cached.
 */
class PlaceSearchRepository(
    context: Context,
    private val nominatim: NominatimClient = NominatimClient(),
    private val photon: PhotonClient = PhotonClient(),
    private val historyRepo: HistoryRepository = HistoryRepository(context)
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val memoryCache = object : LinkedHashMap<String, List<PlaceResult>>(LRU_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PlaceResult>>?) =
            size > LRU_CAPACITY
    }

    /** Local saved places + recent trip destinations matching [query] (case-insensitive contains). */
    suspend fun searchLocal(query: String): List<PlaceResult> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val saved = runCatching { historyRepo.getAllSavedPlacesOnce() }.getOrDefault(emptyList())
            .filter { it.name.lowercase().contains(q) || it.label.lowercase().contains(q) }
            .map { PlaceResult(it.name, LatLon(it.lat, it.lon), tag = "Saved") }
        val recent = runCatching { historyRepo.getAllTripsOnce() }.getOrDefault(emptyList())
            .filter { it.toLabel.lowercase().contains(q) }
            .distinctBy { it.toLabel }
            .take(8)
            .map { PlaceResult(it.toLabel, LatLon(it.toLat, it.toLon), tag = "Recent") }
        return saved + recent
    }

    /** Local-only results, shown as soon as the field gains focus, before any typing. */
    suspend fun browseLocal(): List<PlaceResult> {
        val saved = runCatching { historyRepo.getAllSavedPlacesOnce() }.getOrDefault(emptyList())
            .map { PlaceResult(it.name, LatLon(it.lat, it.lon), tag = "Saved") }
        val recent = runCatching { historyRepo.getAllTripsOnce() }.getOrDefault(emptyList())
            .distinctBy { it.toLabel }
            .take(8)
            .map { PlaceResult(it.toLabel, LatLon(it.toLat, it.toLon), tag = "Recent") }
        return saved + recent
    }

    /**
     * Full chain: local, then Photon and Nominatim in parallel (each capped at 6s), merged and
     * de-duplicated. Never throws — a provider failing just means fewer results, not a crash.
     */
    suspend fun search(query: String, near: LatLon? = null, countryCode: String? = null): List<PlaceResult> {
        if (query.trim().length < 3) return searchLocal(query)

        val cacheKey = query.trim().lowercase()
        memoryCache[cacheKey]?.let { return it }
        readCache(cacheKey)?.let {
            memoryCache[cacheKey] = it
            return it
        }

        val results = coroutineScope {
            val local = async { runCatching { searchLocal(query) }.getOrDefault(emptyList()) }
            val photonDeferred: Deferred<List<PlaceResult>> = async {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    runCatching { photon.search(query, near) }.getOrDefault(emptyList())
                } ?: emptyList()
            }
            val nominatimDeferred: Deferred<List<PlaceResult>> = async {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    runCatching { nominatim.search(query, countryCode, near) }.getOrDefault(emptyList())
                } ?: emptyList()
            }
            mergePlaceResults(local.await(), photonDeferred.await(), nominatimDeferred.await(), near)
        }

        memoryCache[cacheKey] = results
        writeCache(cacheKey, results)
        return results
    }

    private suspend fun readCache(key: String): List<PlaceResult>? {
        val entry = runCatching { historyRepo.getPlaceCache(key) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - entry.cachedAt > CACHE_TTL_MS) return null
        return runCatching {
            json.decodeFromString<List<CachedResult>>(entry.resultsJson).map {
                PlaceResult(it.label, LatLon(it.lat, it.lon))
            }
        }.getOrNull()
    }

    private suspend fun writeCache(key: String, results: List<PlaceResult>) {
        if (results.isEmpty()) return
        runCatching {
            val payload = json.encodeToString(results.map { CachedResult(it.label, it.location.lat, it.location.lon) })
            historyRepo.putPlaceCache(PlaceCache(key, payload, System.currentTimeMillis()))
        }
    }
}
