package com.joyel.tripguard.share

import com.joyel.tripguard.geo.LatLon

/**
 * A destination [ShareIntentParser] resolved from pasted/shared text. [label] is always set to
 * something showable (a place name, a reverse-geocoded address, or — only as a last resort —
 * the raw coordinates); [isCoordinateLabel] tells the UI to show that last case in small grey
 * text instead of as the main label, per the "never show raw coordinates as the main label" rule.
 */
data class ParsedDestination(
    val location: LatLon,
    val label: String,
    val isCoordinateLabel: Boolean = false,
    val addressLine: String? = null
)
