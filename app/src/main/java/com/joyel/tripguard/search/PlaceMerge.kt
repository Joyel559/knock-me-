package com.joyel.tripguard.search

import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.geo.haversineMeters

private const val DEDUPE_RADIUS_M = 100.0

/**
 * Merges provider result lists (already in rank order: local, then photon, then nominatim),
 * dropping a later hit if an earlier one has the same name within 100m, and sorting local
 * matches first, then by distance to [near] when known. Pure/no Android deps, so it's unit
 * testable without a device.
 */
fun mergePlaceResults(
    local: List<PlaceResult>,
    photon: List<PlaceResult>,
    nominatim: List<PlaceResult>,
    near: LatLon? = null
): List<PlaceResult> {
    val ordered = local + photon + nominatim
    val kept = mutableListOf<PlaceResult>()
    for (candidate in ordered) {
        val isDuplicate = kept.any {
            sameName(it.label, candidate.label) && haversineMeters(it.location, candidate.location) <= DEDUPE_RADIUS_M
        }
        if (!isDuplicate) kept += candidate
    }
    return if (near != null) {
        kept.sortedWith(
            compareBy(
                { if (it.tag != null) 0 else 1 },
                { haversineMeters(it.location, near) }
            )
        )
    } else kept
}

private fun sameName(a: String, b: String): Boolean =
    a.substringBefore(',').trim().equals(b.substringBefore(',').trim(), ignoreCase = true)
