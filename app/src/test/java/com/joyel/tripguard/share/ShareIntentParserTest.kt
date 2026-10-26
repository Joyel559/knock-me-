package com.joyel.tripguard.share

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareIntentParserTest {
    private val parser = ShareIntentParser()

    @Test
    fun `parses a plain geo URI`() = runTest {
        val result = parser.parse("geo:12.9716,77.5946")
        assertEquals(12.9716, result!!.lat, 0.0001)
        assertEquals(77.5946, result.lon, 0.0001)
    }

    @Test
    fun `parses a google maps at-coordinates URL`() = runTest {
        val result = parser.parse("https://www.google.com/maps/@12.9716,77.5946,15z")
        assertEquals(12.9716, result!!.lat, 0.0001)
        assertEquals(77.5946, result.lon, 0.0001)
    }

    @Test
    fun `parses a google maps query-coordinates URL`() = runTest {
        val result = parser.parse("https://maps.google.com/?q=12.9716,77.5946")
        assertEquals(12.9716, result!!.lat, 0.0001)
        assertEquals(77.5946, result.lon, 0.0001)
    }

    @Test
    fun `returns null for text with no coordinates`() = runTest {
        assertNull(parser.parse("let's meet at the usual place"))
    }

    @Test
    fun `returns null for blank text`() = runTest {
        assertNull(parser.parse(""))
        assertNull(parser.parse(null))
    }
}
