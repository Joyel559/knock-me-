package com.joyel.tripguard.share

/** One-shot holder: MainActivity writes it from an incoming share intent, TripSetupScreen reads+clears it. */
object SharedDestinationHolder {
    var pending: ParsedDestination? = null
    var parseFailed: Boolean = false
    var resolving: Boolean = false
    /** The raw shared text, kept only so the To field can fall back to it as a search query
     * when parsing fails (never lose the shared place, even unresolved). */
    var rawText: String? = null
    /** Which field this share should fill — FROM when it answered a pending "Choose in Google
     * Maps" pick for From, TO otherwise (including every spontaneous share with no pending pick). */
    var target: PickTarget = PickTarget.TO
    /** Whether [target] was TO only because there was no pending pick (not an explicit TO pick) —
     * drives the "Use as start instead" button on the TO place card. */
    var targetWasDefault: Boolean = false
}
