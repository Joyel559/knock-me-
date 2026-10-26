package com.joyel.tripguard.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

private const val EARTH_RADIUS_METERS = 6371000.0

/** Great-circle distance between two points, in meters. */
fun haversineMeters(a: LatLon, b: LatLon): Double {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)

    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(h), sqrt(1 - h))
    return EARTH_RADIUS_METERS * c
}

/**
 * Minimum distance in meters from [point] to the line segment [a]-[b], using an
 * equirectangular projection centered on [point]. Good enough at city/route scale.
 */
fun pointToSegmentMeters(point: LatLon, a: LatLon, b: LatLon): Double {
    val lat0 = Math.toRadians(point.lat)
    val cosLat0 = cos(lat0)

    fun project(p: LatLon): Pair<Double, Double> {
        val x = Math.toRadians(p.lon - point.lon) * cosLat0 * EARTH_RADIUS_METERS
        val y = Math.toRadians(p.lat - point.lat) * EARTH_RADIUS_METERS
        return x to y
    }

    val (px, py) = project(point) // always (0, 0)
    val (ax, ay) = project(a)
    val (bx, by) = project(b)

    val dx = bx - ax
    val dy = by - ay
    val lenSq = dx * dx + dy * dy

    if (lenSq == 0.0) {
        val ddx = px - ax
        val ddy = py - ay
        return sqrt(ddx * ddx + ddy * ddy)
    }

    var t = ((px - ax) * dx + (py - ay) * dy) / lenSq
    t = t.coerceIn(0.0, 1.0)

    val closestX = ax + t * dx
    val closestY = ay + t * dy
    val ddx = px - closestX
    val ddy = py - closestY
    return sqrt(ddx * ddx + ddy * ddy)
}

/** Minimum distance in meters from [point] to a polyline made of consecutive [points]. */
fun pointToPolylineMeters(point: LatLon, points: List<LatLon>): Double {
    require(points.size >= 2) { "polyline needs at least 2 points" }
    var min = Double.MAX_VALUE
    for (i in 0 until points.size - 1) {
        val d = pointToSegmentMeters(point, points[i], points[i + 1])
        if (d < min) min = d
    }
    return min
}
