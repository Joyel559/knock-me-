package com.joyel.tripguard.search

import com.joyel.tripguard.geo.LatLon

/** One search/geocode hit. [tag] is "Saved" or "Recent" for local matches, null for geocoded ones. */
data class PlaceResult(val label: String, val location: LatLon, val tag: String? = null)
