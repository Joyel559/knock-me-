package com.joyel.tripguard.share

/** One-shot holder: MainActivity writes the raw share text + which field it's for, TripSetupScreen
 * reads+clears it and feeds [rawText] into [PlaceInputResolver.resolve] itself — resolving (the
 * network work) happens in TripSetupScreen, not here, so this holder stays a trivial handoff. */
object SharedDestinationHolder {
    var rawText: String? = null
    /** Which field this share should fill — FROM when it answered a pending "Choose in Google
     * Maps" pick for From, TO otherwise (including every spontaneous share with no pending pick). */
    var target: PickTarget = PickTarget.TO
    /** Whether [target] was TO only because there was no pending pick (not an explicit TO pick) —
     * drives the "Use as start instead" button on the TO place card. */
    var targetWasDefault: Boolean = false
}
