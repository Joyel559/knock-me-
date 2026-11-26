package com.joyel.tripguard.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineParseTest {

    @Test
    fun `extracts pin coordinates from a full maps URL with !3d!4d`() {
        val url = "https://www.google.com/maps/place/Lalbagh+Botanical+Garden/@12.9507,77.5848,17z/data=!3d12.9500!4d77.5850"
        val coords = OfflineParse.extractCoordsFromMapsUrl(url)
        assertEquals(12.9500, coords!!.lat, 0.0001)
        assertEquals(77.5850, coords.lon, 0.0001)
        assertEquals("Lalbagh Botanical Garden", OfflineParse.extractNameFromMapsUrl(url))
    }

    @Test
    fun `extracts at-coordinates when there is no pin`() {
        val coords = OfflineParse.extractCoordsFromMapsUrl("https://www.google.com/maps/@12.9716,77.5946,15z")
        assertEquals(12.9716, coords!!.lat, 0.0001)
        assertEquals(77.5946, coords.lon, 0.0001)
    }

    @Test
    fun `extracts q=lat,lon`() {
        val coords = OfflineParse.extractCoordsFromMapsUrl("https://maps.google.com/?q=12.9716,77.5946")
        assertEquals(12.9716, coords!!.lat, 0.0001)
        assertEquals(77.5946, coords.lon, 0.0001)
    }

    @Test
    fun `an ftid-only URL has a name but no coordinates`() {
        val url = "https://www.google.com/maps/place/SK-+Taste+of+UDUPI,+Subhash+Chandra+Bose+Rd/data=!4m2!3m1!1s0x3b087367491cf547:0x5b0950400f35acfa!18m1!1e1"
        assertNull(OfflineParse.extractCoordsFromMapsUrl(url))
        assertEquals("SK- Taste of UDUPI, Subhash Chandra Bose Rd", OfflineParse.extractNameFromMapsUrl(url))
    }

    @Test
    fun `geo URI with q=name is a name query, not coordinates`() {
        assertEquals("Taj Mahal", OfflineParse.parseGeoUriQuery("geo:0,0?q=Taj%20Mahal"))
    }

    @Test
    fun `geo URI with bare coordinates`() {
        val coords = OfflineParse.parseGeoUri("geo:12.9716,77.5946")
        assertEquals(12.9716, coords!!.lat, 0.0001)
        assertEquals(77.5946, coords.lon, 0.0001)
    }

    @Test
    fun `decimal coordinates from a dropped-pin copy`() {
        val coords = OfflineParse.parseDecimalCoordinates("9.9816, 76.2999")
        assertEquals(9.9816, coords!!.lat, 0.0001)
        assertEquals(76.2999, coords.lon, 0.0001)
    }

    @Test
    fun `DMS coordinates`() {
        val coords = OfflineParse.parseDms("""12°58'17.8"N, 77°35'40.6"E""")
        assertEquals(12.9716, coords!!.lat, 0.001)
        assertEquals(77.5946, coords.lon, 0.001)
    }

    @Test
    fun `finds a Plus Code token in surrounding text`() {
        assertEquals("7FG49QCJ+2VX", OfflineParse.findPlusCodeToken("near 7FG49QCJ+2VX, thanks"))
    }

    @Test
    fun `shared text with name, address and a link splits into the right lines`() {
        val lines = OfflineParse.nonUrlLines("Cafe Noir\nMG Road, Bengaluru\nhttps://maps.app.goo.gl/abc")
        assertEquals(listOf("Cafe Noir", "MG Road, Bengaluru"), lines)
    }

    @Test
    fun `isShortLinkUrl recognizes every short host, isFullMapsHost recognizes the rest`() {
        assertTrue(OfflineParse.isShortLinkUrl("https://maps.app.goo.gl/8n1Re7AfzJGhUWDy9"))
        assertTrue(OfflineParse.isShortLinkUrl("https://goo.gl/maps/xyz"))
        assertTrue(OfflineParse.isShortLinkUrl("https://g.co/kgs/xyz"))
        assertTrue(OfflineParse.isShortLinkUrl("https://share.google/xyz"))
        assertTrue(OfflineParse.isFullMapsHost("https://www.google.com/maps/place/X"))
        assertTrue(OfflineParse.isFullMapsHost("https://maps.google.com/?q=1,2"))
    }

    @Test
    fun `looksLikePlaceText and shareSourceLabel`() {
        assertTrue(looksLikePlaceText("https://maps.app.goo.gl/abc"))
        assertTrue(looksLikePlaceText("9.9816, 76.2999"))
        assertEquals("Google Maps", shareSourceLabel("Cafe Noir\nhttps://maps.app.goo.gl/abc"))
    }
}
