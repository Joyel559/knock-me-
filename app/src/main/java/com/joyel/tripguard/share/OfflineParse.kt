package com.joyel.tripguard.share

import com.joyel.tripguard.geo.LatLon
import java.net.URLDecoder

/**
 * Pure, offline (no network, no suspend) text/URL parsing — the regex patterns [PlaceInputResolver]
 * needs for Step A (full offline parse) and for pulling a name/pin out of a short link's final
 * redirect target. Kept free of coroutines/Android so it's trivially unit-testable.
 */
object OfflineParse {

    private val URL_PATTERN = Regex("""https?://\S+""")
    private val GEO_QZERO_PATTERN = Regex("""geo:0,0\?q=([^&\s]+)""", RegexOption.IGNORE_CASE)
    private val GEO_COORD_PATTERN = Regex("""geo:(-?\d+\.?\d*),(-?\d+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val PIN_PATTERN = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private val AT_PATTERN = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val KEYED_COORD_PATTERN = Regex("""[?&](?:q|ll|query|destination|daddr)=(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val KEYED_TEXT_PATTERN = Regex("""[?&](?:q|query)=([^&]+)""")
    private val PLACE_NAME_PATTERN = Regex("""/maps/place/([^/?#]+)""")
    private val DECIMAL_COORD_PATTERN = Regex("""^\s*(-?\d{1,3}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)\s*$""")
    private val DMS_PATTERN = Regex(
        """(\d{1,3})[°\s]+(\d{1,2})['\s]+(\d{1,2}(?:\.\d+)?)["\s]*([NSns])[,\s]+(\d{1,3})[°\s]+(\d{1,2})['\s]+(\d{1,2}(?:\.\d+)?)["\s]*([EWew])"""
    )
    val PLUS_CODE_PATTERN: Regex =
        Regex("""([23456789CFGHJMPQRVWXcfghjmpqrvwx]{0,8}\+[23456789CFGHJMPQRVWXcfghjmpqrvwx]{2,7})(?![23456789CFGHJMPQRVWXcfghjmpqrvwx])""")

    private val FULL_MAPS_HOST_PREFIXES = listOf("maps.google.", "www.google.", "google.")
    /** Hosts that only carry an opaque id — must be resolved (follow redirect / unshortener)
     * before anything useful is known, per Part 0's real test. */
    private val SHORT_LINK_HOSTS = listOf("maps.app.goo.gl", "goo.gl", "g.co", "share.google")

    fun extractUrl(text: String): String? = URL_PATTERN.find(text)?.value

    fun isShortLinkUrl(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return SHORT_LINK_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    fun isFullMapsHost(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return FULL_MAPS_HOST_PREFIXES.any { host.startsWith(it) }
    }

    fun hostOf(url: String): String? = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()

    /** geo:0,0?q=<name or coords>, or geo:<lat>,<lon> directly. */
    fun parseGeoUri(text: String): LatLon? {
        GEO_COORD_PATTERN.find(text)?.let { m ->
            val lat = m.groupValues[1].toDoubleOrNull()
            val lon = m.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null && !(lat == 0.0 && lon == 0.0)) return LatLon(lat, lon)
        }
        return null
    }

    /** The query text after `geo:0,0?q=`, decoded — a name or "lat,lon", never both at once here. */
    fun parseGeoUriQuery(text: String): String? =
        GEO_QZERO_PATTERN.find(text)?.let { decode(it.groupValues[1]) }

    fun parseDecimalCoordinates(text: String): LatLon? {
        val m = DECIMAL_COORD_PATTERN.find(text.trim()) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        return if (lat in -90.0..90.0 && lon in -180.0..180.0) LatLon(lat, lon) else null
    }

    fun parseDms(text: String): LatLon? {
        val m = DMS_PATTERN.find(text) ?: return null
        val latDeg = m.groupValues[1].toDouble()
        val latMin = m.groupValues[2].toDouble()
        val latSec = m.groupValues[3].toDouble()
        val latDir = m.groupValues[4]
        val lonDeg = m.groupValues[5].toDouble()
        val lonMin = m.groupValues[6].toDouble()
        val lonSec = m.groupValues[7].toDouble()
        val lonDir = m.groupValues[8]
        var lat = latDeg + latMin / 60.0 + latSec / 3600.0
        var lon = lonDeg + lonMin / 60.0 + lonSec / 3600.0
        if (latDir.equals("S", ignoreCase = true)) lat = -lat
        if (lonDir.equals("W", ignoreCase = true)) lon = -lon
        return LatLon(lat, lon)
    }

    fun findPlusCodeToken(text: String): String? = PLUS_CODE_PATTERN.find(text)?.value?.uppercase()

    /** Pin priority inside a full Maps URL: `!3d!4d`, then `q`/`ll`/`query`/`destination`/`daddr`, then `@lat,lon`. */
    fun extractCoordsFromMapsUrl(url: String): LatLon? {
        PIN_PATTERN.find(url)?.let { m -> return LatLon(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()) }
        KEYED_COORD_PATTERN.find(url)?.let { m -> return LatLon(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()) }
        AT_PATTERN.find(url)?.let { m -> return LatLon(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()) }
        return null
    }

    /** Name from `/maps/place/<Name>/` (preferred) or a non-coordinate `q=`/`query=` value. */
    fun extractNameFromMapsUrl(url: String): String? {
        PLACE_NAME_PATTERN.find(url)?.let { m ->
            return decode(m.groupValues[1]).replace('+', ' ').trim().takeIf { it.isNotBlank() }
        }
        KEYED_TEXT_PATTERN.find(url)?.let { m ->
            val value = decode(m.groupValues[1])
            if (DECIMAL_COORD_PATTERN.matches(value)) return null
            return value.replace('+', ' ').trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    /** Lines of shared text that aren't a URL — candidate name (first line) + address (the rest). */
    fun nonUrlLines(text: String): List<String> =
        text.trim().lines().map { it.trim() }.filter { it.isNotBlank() && !URL_PATTERN.containsMatchIn(it) }

    fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    fun coordinateLabel(lat: Double, lon: Double): String = "%.5f, %.5f".format(lat, lon)
}

/** Cheap, offline pattern check (no geocoding/network) used by the clipboard-offer sheet to decide
 * whether copied text is worth asking about: a Maps link/short link, a `geo:` URI, plain
 * coordinates, or a Plus Code. Does not guarantee [PlaceInputResolver.resolve] will find a place. */
fun looksLikePlaceText(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val trimmed = text.trim()
    return OfflineParse.extractUrl(trimmed) != null ||
        trimmed.startsWith("geo:", ignoreCase = true) ||
        OfflineParse.parseDecimalCoordinates(trimmed) != null ||
        OfflineParse.PLUS_CODE_PATTERN.containsMatchIn(trimmed)
}

/** Best-effort "Shared from X" label for the Trip Setup place card, from the raw shared text. */
fun shareSourceLabel(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val url = OfflineParse.extractUrl(text) ?: return null
    val host = OfflineParse.hostOf(url) ?: return null
    return when {
        "goo.gl" in host || "google.com" in host || "g.co" in host || "share.google" in host -> "Google Maps"
        else -> host
    }
}
