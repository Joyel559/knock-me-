package com.joyel.tripguard.share

import android.content.Context
import com.joyel.tripguard.history.HistoryRepository
import com.joyel.tripguard.history.LinkCacheEntry

private const val LINK_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
private const val LRU_CAPACITY = 50

interface LinkCache {
    suspend fun get(url: String): Place?
    suspend fun put(url: String, place: Place)
}

/** No-op default for tests — [PlaceInputResolver] works fine without a cache, just slower. */
object NoopLinkCache : LinkCache {
    override suspend fun get(url: String): Place? = null
    override suspend fun put(url: String, place: Place) = Unit
}

/** Room-backed [LinkCache] (30-day TTL) with an in-memory LRU in front, same shape as
 * [com.joyel.tripguard.search.PlaceSearchRepository]'s place-search cache. */
class RoomLinkCache(context: Context) : LinkCache {
    private val historyRepo = HistoryRepository(context)
    private val memoryCache = object : LinkedHashMap<String, Place>(LRU_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Place>?) = size > LRU_CAPACITY
    }

    override suspend fun get(url: String): Place? {
        memoryCache[url]?.let { return it }
        val entry = runCatching { historyRepo.getLinkCache(url) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - entry.resolvedAt > LINK_CACHE_TTL_MS) return null
        val place = Place(entry.lat, entry.lon, entry.name, entry.address)
        memoryCache[url] = place
        return place
    }

    override suspend fun put(url: String, place: Place) {
        memoryCache[url] = place
        runCatching {
            historyRepo.putLinkCache(
                LinkCacheEntry(url, place.lat, place.lon, place.name, place.address, System.currentTimeMillis())
            )
        }
    }
}
