package com.joyel.tripguard.ui

import com.joyel.tripguard.trip.TripState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure unit test for the replace-active-trip decision a new share makes in [MainActivity]. */
class MainActivityShareTest {

    @Test
    fun `prompts to replace only when a trip is actually running`() {
        assertTrue(shouldPromptReplaceTrip(TripState.ACTIVE))
        assertTrue(shouldPromptReplaceTrip(TripState.ARRIVAL_ALARMING))
        assertTrue(shouldPromptReplaceTrip(TripState.ROUTE_ALARMING))
        assertFalse(shouldPromptReplaceTrip(TripState.FINISHED))
        assertFalse(shouldPromptReplaceTrip(TripState.IDLE))
        assertFalse(shouldPromptReplaceTrip(null))
    }
}
