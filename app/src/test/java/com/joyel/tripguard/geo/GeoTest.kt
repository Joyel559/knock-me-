package com.joyel.tripguard.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun `haversine same point is zero`() {
        val p = LatLon(12.9716, 77.5946)
        assertEquals(0.0, haversineMeters(p, p), 0.001)
    }

    @Test
    fun `haversine known distance approx`() {
        // Bengaluru to Chennai, approx 290 km great-circle
        val bengaluru = LatLon(12.9716, 77.5946)
        val chennai = LatLon(13.0827, 80.2707)
        val d = haversineMeters(bengaluru, chennai)
        assertTrue("expected ~290km, got $d", d in 280_000.0..300_000.0)
    }

    @Test
    fun `point to segment distance for point on segment is zero`() {
        val a = LatLon(0.0, 0.0)
        val b = LatLon(0.0, 1.0)
        val mid = LatLon(0.0, 0.5)
        assertEquals(0.0, pointToSegmentMeters(mid, a, b), 1.0)
    }

    @Test
    fun `point to segment distance for perpendicular offset`() {
        val a = LatLon(0.0, 0.0)
        val b = LatLon(0.0, 1.0)
        // ~0.01 deg lat offset at the equator is ~1113m
        val off = LatLon(0.01, 0.5)
        val d = pointToSegmentMeters(off, a, b)
        assertTrue("expected ~1113m, got $d", d in 1000.0..1200.0)
    }

    @Test
    fun `point to segment clamps beyond endpoints`() {
        val a = LatLon(0.0, 0.0)
        val b = LatLon(0.0, 1.0)
        val beyond = LatLon(0.0, 2.0)
        val d = pointToSegmentMeters(beyond, a, b)
        val directToB = haversineMeters(beyond, b)
        assertEquals(directToB, d, 1.0)
    }

    @Test
    fun `point to polyline picks nearest segment`() {
        val points = listOf(
            LatLon(0.0, 0.0),
            LatLon(0.0, 1.0),
            LatLon(1.0, 1.0)
        )
        val near = LatLon(0.5, 1.001)
        val d = pointToPolylineMeters(near, points)
        assertTrue("expected small distance, got $d", d < 200.0)
    }
}
