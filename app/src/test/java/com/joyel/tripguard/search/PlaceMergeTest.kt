package com.joyel.tripguard.search

import com.joyel.tripguard.geo.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMergeTest {

    @Test
    fun `local results always come before photon and nominatim`() {
        val local = listOf(PlaceResult("Home", LatLon(1.0, 1.0), tag = "Saved"))
        val photon = listOf(PlaceResult("Cafe X", LatLon(2.0, 2.0)))
        val nominatim = listOf(PlaceResult("Cafe Y", LatLon(3.0, 3.0)))

        val merged = mergePlaceResults(local, photon, nominatim)

        assertEquals("Home", merged[0].label)
        assertEquals("Cafe X", merged[1].label)
        assertEquals("Cafe Y", merged[2].label)
    }

    @Test
    fun `same name within 100m is deduped, keeping the earlier (higher-ranked) provider`() {
        val local = listOf(PlaceResult("Central Hospital", LatLon(12.9716, 77.5946), tag = "Saved"))
        val photon = listOf(PlaceResult("Central Hospital", LatLon(12.9717, 77.5947))) // ~15m away
        val nominatim = listOf(PlaceResult("Different Place", LatLon(13.0, 78.0)))

        val merged = mergePlaceResults(local, photon, nominatim)

        assertEquals(2, merged.size)
        assertEquals("Saved", merged.first { it.label == "Central Hospital" }.tag)
    }

    @Test
    fun `same name far apart (over 100m) is kept as two separate results`() {
        val photon = listOf(PlaceResult("City Bus Stand", LatLon(12.9716, 77.5946)))
        val nominatim = listOf(PlaceResult("City Bus Stand", LatLon(13.05, 77.6))) // several km away

        val merged = mergePlaceResults(emptyList(), photon, nominatim)

        assertEquals(2, merged.size)
    }

    @Test
    fun `when a current location is known, non-local results are sorted by distance`() {
        val near = LatLon(0.0, 0.0)
        val photon = listOf(
            PlaceResult("Far", LatLon(1.0, 1.0)),
            PlaceResult("Near", LatLon(0.01, 0.01))
        )

        val merged = mergePlaceResults(emptyList(), photon, emptyList(), near)

        assertEquals("Near", merged[0].label)
        assertEquals("Far", merged[1].label)
    }

    @Test
    fun `empty provider lists never crash and just produce no results`() {
        assertTrue(mergePlaceResults(emptyList(), emptyList(), emptyList()).isEmpty())
    }
}
