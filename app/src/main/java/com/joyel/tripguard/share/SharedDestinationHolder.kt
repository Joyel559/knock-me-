package com.joyel.tripguard.share

import com.joyel.tripguard.geo.LatLon

/** One-shot holder: MainActivity writes it from an incoming share intent, TripSetupScreen reads+clears it. */
object SharedDestinationHolder {
    var pending: LatLon? = null
    var parseFailed: Boolean = false
    var resolving: Boolean = false
}
