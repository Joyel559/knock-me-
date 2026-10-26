package com.joyel.tripguard.trip

import com.joyel.tripguard.geo.LatLon

data class TripPrefill(val location: LatLon, val label: String, val mode: Mode, val radiusMeters: Int)

/** One-shot handoff from History/Saved-places "repeat trip" taps into [com.joyel.tripguard.ui.screens.TripSetupScreen]. */
object TripPrefillHolder {
    var pending: TripPrefill? = null
}
