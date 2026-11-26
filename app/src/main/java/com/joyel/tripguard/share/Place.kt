package com.joyel.tripguard.share

import com.joyel.tripguard.geo.LatLon

/** A resolved place. [isCoordinateLabel] means [name] is just formatted coordinates (last
 * resort) — the UI shows that in small grey text instead of as the main label, same rule v1.4
 * established. [address] is a second line shown under the name when known. */
data class Place(
    val lat: Double,
    val lon: Double,
    val name: String,
    val address: String? = null,
    val isCoordinateLabel: Boolean = false,
    /** Host the share/link came from (e.g. "Google Maps"), shown as "Shared from X". */
    val source: String? = null
) {
    val location: LatLon get() = LatLon(lat, lon)
}

/** [PlaceInputResolver.resolve]'s three states. [Reading.name] is whatever name could be read
 * from the shared text with no network (so the field never sits empty/showing a raw URL). */
sealed class ResolveState {
    data class Reading(val name: String? = null) : ResolveState()
    data class Found(val place: Place) : ResolveState()
    object Failed : ResolveState()
}
