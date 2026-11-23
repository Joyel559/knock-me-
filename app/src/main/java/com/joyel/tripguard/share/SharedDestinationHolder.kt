package com.joyel.tripguard.share

/** One-shot holder: MainActivity writes it from an incoming share intent, TripSetupScreen reads+clears it. */
object SharedDestinationHolder {
    var pending: ParsedDestination? = null
    var parseFailed: Boolean = false
    var resolving: Boolean = false
    /** The raw shared text, kept only so the To field can fall back to it as a search query
     * when parsing fails (never lose the shared place, even unresolved). */
    var rawText: String? = null
}
