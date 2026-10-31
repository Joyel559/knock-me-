package com.joyel.tripguard.share

import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.search.NominatimClient
import com.joyel.tripguard.search.PhotonClient
import com.joyel.tripguard.search.PlaceResult
import kotlinx.coroutines.withTimeoutOrNull

private const val PROVIDER_TIMEOUT_MS = 6_000L

/** The search/reverse-geocode surface [ShareIntentParser] needs, kept small and behind an
 * interface so tests can fake it instead of hitting the network. */
interface GeocodeProvider {
    suspend fun search(query: String, near: LatLon?): List<PlaceResult>
    suspend fun reverse(lat: Double, lon: Double): String?
}

/** No-API-key default: Photon first, Nominatim as backup (same providers as part 1's search,
 * without the local saved/recent layer, which needs a Context the parser doesn't have). */
class DefaultGeocodeProvider(
    private val nominatim: NominatimClient = NominatimClient(),
    private val photon: PhotonClient = PhotonClient()
) : GeocodeProvider {
    override suspend fun search(query: String, near: LatLon?): List<PlaceResult> {
        val photonResults = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
            runCatching { photon.search(query, near) }.getOrDefault(emptyList())
        }.orEmpty()
        if (photonResults.isNotEmpty()) return photonResults
        return withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
            runCatching { nominatim.search(query, near = near) }.getOrDefault(emptyList())
        }.orEmpty()
    }

    override suspend fun reverse(lat: Double, lon: Double): String? =
        runCatching { photon.reverse(lat, lon) }.getOrNull()
            ?: runCatching { nominatim.reverseGeocode(lat, lon) }.getOrNull()
}
