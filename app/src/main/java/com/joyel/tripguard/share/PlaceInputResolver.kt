package com.joyel.tripguard.share

import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.geo.PlusCode
import com.joyel.tripguard.geo.haversineMeters
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

private const val RESOLVE_TIMEOUT_MS = 4_000L
private const val REVERSE_TIMEOUT_MS = 2_000L

/**
 * The single entry point every share/paste/clipboard/typed-link path calls (v2.0). Emits
 * [ResolveState.Reading] immediately, then [ResolveState.Found] as soon as a valid result
 * arrives (replacing it again later if a coordinate-only result gets a real name from a
 * background reverse geocode — Step E), or [ResolveState.Failed] if nothing panned out inside
 * the 4s budget. Never throws.
 */
class PlaceInputResolver(
    private val geocode: GeocodeProvider = DefaultGeocodeProvider(),
    private val localResolver: ShortLinkResolver = LocalRedirectResolver(),
    private val onlineResolver: ShortLinkResolver = OnlineUnshortenerResolver(),
    private val linkCache: LinkCache = NoopLinkCache,
    private val onlineResolverEnabled: suspend () -> Boolean = { true },
    private val debugLog: (LinkDebugEntry) -> Unit = {}
) {

    fun resolve(rawText: String?, near: LatLon? = null, countryCode: String? = null): Flow<ResolveState> = flow {
        if (rawText.isNullOrBlank()) {
            emit(ResolveState.Failed)
            return@flow
        }
        val trimmed = rawText.trim()
        emit(ResolveState.Reading(OfflineParse.nonUrlLines(trimmed).firstOrNull()))

        val startedAt = System.currentTimeMillis()
        var resolverWon = "none"
        val place = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            resolveInternal(trimmed, near, countryCode) { resolverWon = it }
        }
        debugLog(
            LinkDebugEntry(
                input = trimmed.take(200),
                resolverWon = resolverWon,
                elapsedMs = System.currentTimeMillis() - startedAt,
                result = place?.let { "${it.name} (${it.lat},${it.lon})" } ?: "failed"
            )
        )

        if (place == null) {
            emit(ResolveState.Failed)
            return@flow
        }
        emit(ResolveState.Found(place))

        // Step E: coords but no real name yet -> reverse geocode in the background, replace the label.
        if (place.isCoordinateLabel) {
            val label = withTimeoutOrNull(REVERSE_TIMEOUT_MS) {
                runCatching { geocode.reverse(place.lat, place.lon) }.getOrNull()
            }
            if (!label.isNullOrBlank()) emit(ResolveState.Found(place.copy(name = label, isCoordinateLabel = false)))
        }
    }

    private suspend fun resolveInternal(
        text: String,
        near: LatLon?,
        countryCode: String?,
        onWinner: (String) -> Unit
    ): Place? {
        // Step A: offline parse, no network.
        offlineFullParse(text, near)?.let { onWinner("offline"); return it }

        val url = OfflineParse.extractUrl(text)
        if (url != null) {
            if (OfflineParse.isShortLinkUrl(url)) {
                linkCache.get(url)?.let { onWinner("cache"); return it }
                val result = raceShortLinkResolvers(url, onWinner)
                if (result != null) {
                    val place = placeFromShortLinkResult(result, near, countryCode)
                    if (place != null) {
                        linkCache.put(url, place)
                        return place
                    }
                }
            } else {
                OfflineParse.extractNameFromMapsUrl(url)?.let { name ->
                    geocodeOne(name, near, countryCode)?.let { onWinner("geocode"); return it }
                }
            }
        }

        // Step D: whatever name/address the shared text itself carries.
        nameAddressQuery(text)?.let { query ->
            geocodeOne(query, near, countryCode)?.let { onWinner("geocode"); return it }
        }
        return null
    }

    /** Resolver 1 (local redirect) and Resolver 2 (online unshortener) in parallel; the first
     * VALID result wins and cancels the loser. If the fast one is invalid/null, keeps waiting
     * for the other instead of giving up. */
    private suspend fun raceShortLinkResolvers(url: String, onWinner: (String) -> Unit): ShortLinkResult? =
        coroutineScope {
            val local = async { runCatching { localResolver.resolve(url) }.getOrNull() }
            val onlineOn = runCatching { onlineResolverEnabled() }.getOrDefault(true)
            val online = async { if (onlineOn) runCatching { onlineResolver.resolve(url) }.getOrNull() else null }

            var localResult: ShortLinkResult? = null
            var onlineResult: ShortLinkResult? = null
            var localDone = false
            var onlineDone = false

            while (!localDone || !onlineDone) {
                select<Unit> {
                    if (!localDone) local.onAwait { localResult = it; localDone = true }
                    if (!onlineDone) online.onAwait { onlineResult = it; onlineDone = true }
                }
                val valid = when {
                    localResult?.isValid() == true -> "local" to localResult
                    onlineResult?.isValid() == true -> "online" to onlineResult
                    else -> null
                }
                if (valid != null) {
                    if (!localDone) local.cancel()
                    if (!onlineDone) online.cancel()
                    onWinner(valid.first)
                    return@coroutineScope valid.second
                }
            }
            null
        }

    private suspend fun placeFromShortLinkResult(r: ShortLinkResult, near: LatLon?, countryCode: String?): Place? {
        if (r.lat != null && r.lon != null) {
            val label = r.name?.takeIf { it.isNotBlank() }
            return if (label != null) {
                Place(r.lat, r.lon, label, source = "Google Maps")
            } else {
                coordPlace(LatLon(r.lat, r.lon), source = "Google Maps")
            }
        }
        if (!r.name.isNullOrBlank()) {
            return geocodeOne(r.name, near, countryCode)?.copy(source = "Google Maps")
        }
        return null
    }

    private suspend fun geocodeOne(query: String, near: LatLon?, countryCode: String?): Place? {
        val results = runCatching { geocode.search(query, near, countryCode) }.getOrDefault(emptyList())
        val best = if (near != null) results.minByOrNull { haversineMeters(it.location, near) } else results.firstOrNull()
        return best?.let { Place(it.location.lat, it.location.lon, it.label) }
    }

    private fun nameAddressQuery(text: String): String? =
        OfflineParse.nonUrlLines(text).takeIf { it.isNotEmpty() }?.joinToString(", ")

    private fun coordPlace(loc: LatLon, source: String? = null): Place =
        Place(loc.lat, loc.lon, OfflineParse.coordinateLabel(loc.lat, loc.lon), isCoordinateLabel = true, source = source)

    private suspend fun offlineFullParse(text: String, near: LatLon?): Place? {
        OfflineParse.parseGeoUriQuery(text)?.let { q ->
            val coords = OfflineParse.parseDecimalCoordinates(q)
            return if (coords != null) coordPlace(coords) else geocodeOne(q, near, null)
        }
        OfflineParse.parseGeoUri(text)?.let { return coordPlace(it) }

        val url = OfflineParse.extractUrl(text)
        if (url != null && OfflineParse.isFullMapsHost(url)) {
            val coords = OfflineParse.extractCoordsFromMapsUrl(url)
            if (coords != null) {
                val name = OfflineParse.extractNameFromMapsUrl(url)
                return if (name != null) {
                    Place(coords.lat, coords.lon, name, source = shareSourceLabel(text))
                } else {
                    coordPlace(coords, source = shareSourceLabel(text))
                }
            }
        }

        OfflineParse.parseDecimalCoordinates(text)?.let { return coordPlace(it) }
        OfflineParse.parseDms(text)?.let { return coordPlace(it) }
        resolvePlusCode(text, near)?.let { return coordPlace(it) }
        return null
    }

    private suspend fun resolvePlusCode(text: String, near: LatLon?): LatLon? {
        val code = OfflineParse.findPlusCodeToken(text) ?: return null
        if (PlusCode.isFull(code)) return PlusCode.decodeToLatLon(code)
        if (!PlusCode.isShort(code)) return null
        val reference = near ?: run {
            val idx = text.indexOf(code)
            val after = if (idx >= 0) text.substring(idx + code.length).trim().trimStart(',', ' ') else ""
            if (after.isBlank()) return null
            val hit = runCatching { geocode.search(after, null) }.getOrDefault(emptyList()).firstOrNull() ?: return null
            hit.location
        }
        val full = PlusCode.recoverNearest(code, reference.lat, reference.lon) ?: return null
        return PlusCode.decodeToLatLon(full)
    }
}
