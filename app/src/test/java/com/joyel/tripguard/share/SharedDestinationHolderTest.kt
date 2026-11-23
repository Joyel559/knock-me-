package com.joyel.tripguard.share

import com.joyel.tripguard.geo.LatLon
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Test

/** [SharedDestinationHolder] is a one-shot holder: TripSetupScreen must read then clear every
 * field so a recomposition/rotation never re-applies the same share twice. */
class SharedDestinationHolderTest {

    @After
    fun reset() {
        SharedDestinationHolder.pending = null
        SharedDestinationHolder.parseFailed = false
        SharedDestinationHolder.resolving = false
        SharedDestinationHolder.rawText = null
    }

    @Test
    fun `consuming the holder clears every field`() {
        SharedDestinationHolder.pending = ParsedDestination(LatLon(1.0, 2.0), "Somewhere")
        SharedDestinationHolder.parseFailed = true
        SharedDestinationHolder.rawText = "raw share text"

        // Simulates TripSetupScreen's consume step.
        val consumed = SharedDestinationHolder.pending
        val failed = SharedDestinationHolder.parseFailed
        SharedDestinationHolder.pending = null
        SharedDestinationHolder.parseFailed = false
        SharedDestinationHolder.rawText = null

        assert(consumed?.label == "Somewhere")
        assert(failed)
        assertNull(SharedDestinationHolder.pending)
        assertNull(SharedDestinationHolder.rawText)
    }
}
