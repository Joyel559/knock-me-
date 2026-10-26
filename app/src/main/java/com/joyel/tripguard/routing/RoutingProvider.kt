package com.joyel.tripguard.routing

import com.joyel.tripguard.geo.LatLon

/** Behind an interface per the PRD so the backend can be swapped later (ORS, Google Routes). */
interface RoutingProvider {
    /** Returns the route polyline, or null if it could not be fetched. */
    suspend fun route(from: LatLon, to: LatLon): List<LatLon>?
}
