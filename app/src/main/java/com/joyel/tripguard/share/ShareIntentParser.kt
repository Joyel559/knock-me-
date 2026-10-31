package com.joyel.tripguard.share

import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.geo.PlusCode
import com.joyel.tripguard.geo.haversineMeters
import java.net.URLDecoder

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
private val PLUS_CODE_PATTERN = Regex("""([23456789CFGHJMPQRVWXcfghjmpqrvwx]{0,8}\+[23456789CFGHJMPQRVWXcfghjmpqrvwx]{2,7})(?![23456789CFGHJMPQRVWXcfghjmpqrvwx])""")

private val FULL_MAPS_HOST_PREFIXES = listOf("maps.google.", "www.google.", "google.")

/**
 * Extracts a destination (coordinates + a human label) from arbitrary shared/pasted text:
 * Google Maps share text or links (short or long), a `geo:` URI, bare "lat, lon" (decimal or
 * DMS), a Plus Code, or a plain place name. Tries each step in order and stops at the first
 * that yields a destination; never throws (every branch is wrapped so a bad input just falls
 * through to the next step, or returns null if nothing matched).
 */
class ShareIntentParser(
    private val redirectResolver: RedirectResolver = OkHttpRedirectResolver(),
    private val geocoder: GeocodeProvider = DefaultGeocodeProvider()
) {

    /** [near] (current location) is used to bias geocoding and to recover short Plus Codes. */
    suspend fun parse(text: String?, near: LatLon? = null): ParsedDestination? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()

        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
        val nonUrlLines = lines.filterNot { URL_PATTERN.containsMatchIn(it) }
        val candidateName = nonUrlLines.getOrNull(0)
        val candidateAddress = nonUrlLines.drop(1).joinToString(", ").takeIf { it.isNotBlank() }

        // b) geo: URI
        GEO_QZERO_PATTERN.find(trimmed)?.let { m ->
            val query = decode(m.groupValues[1])
            return geocodeBest(query, near)
        }
        GEO_COORD_PATTERN.find(trimmed)?.let { m ->
            val lat = m.groupValues[1].toDoubleOrNull()
            val lon = m.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null && !(lat == 0.0 && lon == 0.0)) {
                return labelFor(LatLon(lat, lon))
            }
        }

        // c) http(s) maps / short links
        val urlMatch = URL_PATTERN.find(trimmed)
        if (urlMatch != null) {
            parseMapsLink(urlMatch.value, near)?.let { return it }
        }

        // e) decimal or DMS coordinates
        parseDecimalCoordinates(trimmed)?.let { return labelFor(it) }
        parseDms(trimmed)?.let { return labelFor(it) }

        // f) Plus Codes
        parsePlusCode(trimmed, near)?.let { return labelFor(it) }

        // d) name (+ address) given, no coordinates yet -> geocode
        if (candidateName != null) {
            val query = listOfNotNull(candidateName, candidateAddress).joinToString(", ")
            geocodeBest(query, near)?.let { return it }
        }

        // g) otherwise, treat the whole text as a search query
        return geocodeBest(trimmed, near)
    }

    private suspend fun parseMapsLink(rawUrl: String, near: LatLon?): ParsedDestination? {
        val host = runCatching { java.net.URI(rawUrl).host }.getOrNull()?.lowercase() ?: return null
        val isFullMapsHost = FULL_MAPS_HOST_PREFIXES.any { host.startsWith(it) }
        val finalUrl = if (isFullMapsHost) {
            rawUrl
        } else {
            runCatching { redirectResolver.resolve(rawUrl) }.getOrDefault(rawUrl)
        }

        val pinned = extractCoordsFromUrl(finalUrl)
        val name = extractNameFromUrl(finalUrl)

        if (pinned != null) {
            val reverseLabel = if (name == null) reverseLabel(pinned) else null
            val label = name ?: reverseLabel
            return if (label != null) {
                ParsedDestination(pinned, label)
            } else {
                ParsedDestination(pinned, "%.5f, %.5f".format(pinned.lat, pinned.lon), isCoordinateLabel = true)
            }
        }
        if (name != null) {
            geocodeBest(name, near)?.let { return it }
        }
        return null
    }

    private fun extractCoordsFromUrl(url: String): LatLon? {
        PIN_PATTERN.find(url)?.let { m -> return LatLon(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()) }
        AT_PATTERN.find(url)?.let { m -> return LatLon(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()) }
        KEYED_COORD_PATTERN.find(url)?.let { m -> return LatLon(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()) }
        return null
    }

    private fun extractNameFromUrl(url: String): String? {
        PLACE_NAME_PATTERN.find(url)?.let { m ->
            return decode(m.groupValues[1]).replace('+', ' ').trim().takeIf { it.isNotBlank() }
        }
        KEYED_TEXT_PATTERN.find(url)?.let { m ->
            val value = decode(m.groupValues[1])
            if (DECIMAL_COORD_PATTERN.matches(value)) return null // it's coordinates, not a name
            return value.replace('+', ' ').trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun parseDecimalCoordinates(text: String): LatLon? {
        val m = DECIMAL_COORD_PATTERN.find(text) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        return if (lat in -90.0..90.0 && lon in -180.0..180.0) LatLon(lat, lon) else null
    }

    private fun parseDms(text: String): LatLon? {
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

    private suspend fun parsePlusCode(text: String, near: LatLon?): LatLon? {
        val match = PLUS_CODE_PATTERN.find(text) ?: return null
        val code = match.value.uppercase()
        if (PlusCode.isFull(code)) return PlusCode.decodeToLatLon(code)
        if (!PlusCode.isShort(code)) return null

        val reference = near ?: run {
            // A short code needs a nearby point to resolve against. If there's no device fix,
            // try the locality text that often follows it, e.g. "XJ5X+2R Bengaluru".
            val after = text.substring(match.range.last + 1).trim().trimStart(',', ' ')
            if (after.isBlank()) return null
            val hit = runCatching { geocoder.search(after, null) }.getOrDefault(emptyList()).firstOrNull()
                ?: return null
            hit.location
        }
        val full = PlusCode.recoverNearest(code, reference.lat, reference.lon) ?: return null
        return PlusCode.decodeToLatLon(full)
    }

    private suspend fun geocodeBest(query: String, near: LatLon?): ParsedDestination? {
        if (query.isBlank()) return null
        val results = runCatching { geocoder.search(query, near) }.getOrDefault(emptyList())
        val best = if (near != null) {
            results.minByOrNull { haversineMeters(it.location, near) }
        } else {
            results.firstOrNull()
        }
        return best?.let { ParsedDestination(it.location, it.label) }
    }

    private suspend fun reverseLabel(loc: LatLon): String? =
        runCatching { geocoder.reverse(loc.lat, loc.lon) }.getOrNull()

    private suspend fun labelFor(loc: LatLon): ParsedDestination {
        val label = reverseLabel(loc)
        return if (label != null) {
            ParsedDestination(loc, label)
        } else {
            ParsedDestination(loc, "%.5f, %.5f".format(loc.lat, loc.lon), isCoordinateLabel = true)
        }
    }

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
