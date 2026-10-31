package com.joyel.tripguard.share

/** One-shot holder: MainActivity writes it from an incoming share intent, TripSetupScreen reads+clears it. */
object SharedDestinationHolder {
    var pending: ParsedDestination? = null
    var parseFailed: Boolean = false
    var resolving: Boolean = false
}
