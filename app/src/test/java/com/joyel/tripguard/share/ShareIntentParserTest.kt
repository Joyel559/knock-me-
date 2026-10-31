package com.joyel.tripguard.share

import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.search.PlaceResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRedirectResolver(private val mapping: Map<String, String>) : RedirectResolver {
    override suspend fun resolve(url: String): String = mapping[url] ?: url
}

private class FakeGeocodeProvider(
    private val searchResults: Map<String, List<PlaceResult>> = emptyMap(),
    private val reverseResults: Map<String, String> = emptyMap()
) : GeocodeProvider {
    override suspend fun search(query: String, near: LatLon?): List<PlaceResult> = searchResults[query] ?: emptyList()
    override suspend fun reverse(lat: Double, lon: Double): String? = reverseResults["%.4f,%.4f".format(lat, lon)]
}

class ShareIntentParserTest {

    @Test
    fun `parses a plain geo URI`() = runTest {
        val parser = ShareIntentParser(geocoder = FakeGeocodeProvider())
        val result = parser.parse("geo:12.9716,77.5946")
        assertEquals(12.9716, result!!.location.lat, 0.0001)
        assertEquals(77.5946, result.location.lon, 0.0001)
    }

    @Test
    fun `geo 0,0 with a query falls through to the geocoder`() = runTest {
        val parser = ShareIntentParser(
            geocoder = FakeGeocodeProvider(
                searchResults = mapOf("Taj Mahal" to listOf(PlaceResult("Taj Mahal, Agra", LatLon(27.1751, 78.0421))))
            )
        )
        val result = parser.parse("geo:0,0?q=Taj%20Mahal")
        assertEquals("Taj Mahal, Agra", result!!.label)
        assertEquals(27.1751, result.location.lat, 0.0001)
    }

    @Test
    fun `parses a full maps place URL with pin coordinates (!3d!4d)`() = runTest {
        val parser = ShareIntentParser(geocoder = FakeGeocodeProvider())
        val url = "https://www.google.com/maps/place/Lalbagh+Botanical+Garden/@12.9507,77.5848,17z/data=!3d12.9500!4d77.5850"
        val result = parser.parse(url)
        assertNotNull(result)
        assertEquals("Lalbagh Botanical Garden", result!!.label)
        assertEquals(12.9500, result.location.lat, 0.0001)
        assertEquals(77.5850, result.location.lon, 0.0001)
    }

    @Test
    fun `parses a maps URL with only at-coordinates (no pin)`() = runTest {
        val parser = ShareIntentParser(geocoder = FakeGeocodeProvider())
        val result = parser.parse("https://www.google.com/maps/@12.9716,77.5946,15z")
        assertEquals(12.9716, result!!.location.lat, 0.0001)
        assertEquals(77.5946, result.location.lon, 0.0001)
        assertTrue(result.isCoordinateLabel)
    }

    @Test
    fun `parses a maps URL with q=lat,lon`() = runTest {
        val parser = ShareIntentParser(geocoder = FakeGeocodeProvider())
        val result = parser.parse("https://maps.google.com/?q=12.9716,77.5946")
        assertEquals(12.9716, result!!.location.lat, 0.0001)
        assertEquals(77.5946, result.location.lon, 0.0001)
    }

    @Test
    fun `resolves a short link via the injected redirect resolver`() = runTest {
        val shortUrl = "https://maps.app.goo.gl/abc123"
        val resolver = FakeRedirectResolver(
            mapOf(shortUrl to "https://www.google.com/maps/place/Cafe+Noir/@13.0,77.6,17z/data=!3d13.0001!4d77.6002")
        )
        val parser = ShareIntentParser(redirectResolver = resolver, geocoder = FakeGeocodeProvider())

        val sharedText = "Cafe Noir\nMG Road, Bengaluru\n$shortUrl"
        val result = parser.parse(sharedText)

        assertNotNull(result)
        assertEquals("Cafe Noir", result!!.label)
        assertEquals(13.0001, result.location.lat, 0.0001)
        assertEquals(77.6002, result.location.lon, 0.0001)
    }

    @Test
    fun `parses decimal coordinates with a reverse-geocoded label`() = runTest {
        val parser = ShareIntentParser(
            geocoder = FakeGeocodeProvider(reverseResults = mapOf("12.9716,77.5946" to "Bengaluru, Karnataka"))
        )
        val result = parser.parse("12.9716, 77.5946")
        assertEquals(12.9716, result!!.location.lat, 0.0001)
        assertEquals("Bengaluru, Karnataka", result.label)
        assertTrue(!result.isCoordinateLabel)
    }

    @Test
    fun `parses DMS coordinates`() = runTest {
        val parser = ShareIntentParser(geocoder = FakeGeocodeProvider())
        val result = parser.parse("""12°58'17.8"N, 77°35'40.6"E""")
        assertNotNull(result)
        assertEquals(12.9716, result!!.location.lat, 0.001)
        assertEquals(77.5946, result.location.lon, 0.001)
    }

    @Test
    fun `decodes a full Plus Code`() = runTest {
        val parser = ShareIntentParser(geocoder = FakeGeocodeProvider())
        val result = parser.parse("7FG49QCJ+2VX")
        assertNotNull(result)
        assertEquals(20.3701125, result!!.location.lat, 0.00001)
        assertEquals(2.782234375, result.location.lon, 0.00001)
    }

    @Test
    fun `recovers a short Plus Code using the current location as reference`() = runTest {
        val parser = ShareIntentParser(geocoder = FakeGeocodeProvider())
        val near = LatLon(51.3701125, -1.217765625)
        val result = parser.parse("+2VX", near = near)
        assertNotNull(result)
        assertEquals(51.3701125, result!!.location.lat, 0.0001)
        assertEquals(-1.217765625, result.location.lon, 0.0001)
    }

    @Test
    fun `plain place name is resolved through the geocode chain`() = runTest {
        val parser = ShareIntentParser(
            geocoder = FakeGeocodeProvider(
                searchResults = mapOf("City Central Bus Stand" to listOf(PlaceResult("City Central Bus Stand", LatLon(13.0, 77.6))))
            )
        )
        val result = parser.parse("City Central Bus Stand")
        assertEquals("City Central Bus Stand", result!!.label)
        assertEquals(13.0, result.location.lat, 0.0001)
    }

    @Test
    fun `returns null when nothing matches and the geocoder finds nothing`() = runTest {
        val parser = ShareIntentParser(geocoder = FakeGeocodeProvider())
        assertNull(parser.parse("let's meet at the usual place"))
    }

    @Test
    fun `returns null for blank text`() = runTest {
        val parser = ShareIntentParser(geocoder = FakeGeocodeProvider())
        assertNull(parser.parse(""))
        assertNull(parser.parse(null))
    }
}
