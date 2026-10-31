package com.joyel.tripguard.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vectors taken verbatim from Google's official test data
 * (github.com/google/open-location-code, test_data/encoding.csv and shortCodeTests.csv).
 */
class PlusCodeTest {

    @Test
    fun `decodes a full code to its known center point`() {
        val area = PlusCode.decode("7FG49QCJ+2VX")
        assertNotNull(area)
        assertEquals(20.3701125, area!!.centerLat, 1e-6)
        assertEquals(2.782234375, area.centerLon, 1e-6)
    }

    @Test
    fun `decodes a 10-digit full code to its known center point`() {
        val area = PlusCode.decode("8FVC2222+22")
        assertNotNull(area)
        assertEquals(47.0000625, area!!.centerLat, 1e-6)
        assertEquals(8.0000625, area.centerLon, 1e-6)
    }

    @Test
    fun `decodeToLatLon returns the same center as decode`() {
        val latLon = PlusCode.decodeToLatLon("7FG49QCJ+2VX")
        assertNotNull(latLon)
        assertEquals(20.3701125, latLon!!.lat, 1e-6)
        assertEquals(2.782234375, latLon.lon, 1e-6)
    }

    @Test
    fun `recovers a short code to the known full code near its reference point`() {
        val full = PlusCode.recoverNearest("+2VX", 51.3701125, -1.217765625)
        assertEquals("9C3W9QCJ+2VX", full)
    }

    @Test
    fun `a full code passed to recoverNearest is returned unchanged (uppercased)`() {
        val full = PlusCode.recoverNearest("9c3w9qcj+2vx", 0.0, 0.0)
        assertEquals("9C3W9QCJ+2VX", full)
    }

    @Test
    fun `invalid code returns null instead of throwing`() {
        assertNull(PlusCode.decode("not a code"))
        assertNull(PlusCode.decode(""))
    }

    @Test
    fun `isFull and isShort agree with the reference examples`() {
        assertTrue(PlusCode.isFull("9C3W9QCJ+2VX"))
        assertTrue(PlusCode.isShort("+2VX"))
        assertTrue(PlusCode.isShort("CJ+2VX"))
        assertTrue(!PlusCode.isFull("+2VX"))
    }
}
