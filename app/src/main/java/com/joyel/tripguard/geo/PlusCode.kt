package com.joyel.tripguard.geo

import kotlin.math.floor
import kotlin.math.min

/**
 * Open Location Code (Plus Codes) encode/decode, pure Kotlin port of Google's reference
 * algorithm (github.com/google/open-location-code, python/openlocationcode/openlocationcode.py,
 * Apache 2.0). No Android deps, so it's unit testable on its own.
 */
object PlusCode {
    private const val SEPARATOR = '+'
    private const val SEPARATOR_POSITION = 8
    private const val PADDING_CHAR = '0'
    private const val CODE_ALPHABET = "23456789CFGHJMPQRVWX"
    private val ENCODING_BASE = CODE_ALPHABET.length // 20
    private const val LATITUDE_MAX = 90.0
    private const val LONGITUDE_MAX = 180.0
    private const val PAIR_CODE_LENGTH = 10
    private val PAIR_FIRST_PLACE_VALUE = Math.pow(ENCODING_BASE.toDouble(), (PAIR_CODE_LENGTH / 2 - 1).toDouble()).toLong()
    private val PAIR_PRECISION = Math.pow(ENCODING_BASE.toDouble(), 3.0).toLong()
    private const val MAX_DIGIT_COUNT = 15
    private const val GRID_CODE_LENGTH = MAX_DIGIT_COUNT - PAIR_CODE_LENGTH // 5
    private const val GRID_COLUMNS = 4
    private const val GRID_ROWS = 5
    private val GRID_LAT_FIRST_PLACE_VALUE = Math.pow(GRID_ROWS.toDouble(), (GRID_CODE_LENGTH - 1).toDouble()).toLong()
    private val GRID_LNG_FIRST_PLACE_VALUE = Math.pow(GRID_COLUMNS.toDouble(), (GRID_CODE_LENGTH - 1).toDouble()).toLong()
    private val FINAL_LAT_PRECISION = PAIR_PRECISION * Math.pow(GRID_ROWS.toDouble(), GRID_CODE_LENGTH.toDouble()).toLong()
    private val FINAL_LNG_PRECISION = PAIR_PRECISION * Math.pow(GRID_COLUMNS.toDouble(), GRID_CODE_LENGTH.toDouble()).toLong()

    data class CodeArea(
        val southLat: Double,
        val westLon: Double,
        val northLat: Double,
        val eastLon: Double
    ) {
        val centerLat: Double get() = (southLat + northLat) / 2
        val centerLon: Double get() = (westLon + eastLon) / 2
    }

    fun isValid(code: String): Boolean {
        val sepCount = code.count { it == SEPARATOR }
        if (sepCount != 1) return false
        if (code.length == 1) return false
        val sep = code.indexOf(SEPARATOR)
        if (sep == -1 || sep > SEPARATOR_POSITION || sep % 2 == 1) return false
        val padIndex = code.indexOf(PADDING_CHAR)
        if (padIndex != -1) {
            if (sep < SEPARATOR_POSITION) return false
            if (padIndex == 0) return false
            val rpad = code.lastIndexOf(PADDING_CHAR) + 1
            val pads = code.substring(padIndex, rpad)
            if (pads.length % 2 == 1 || pads.any { it != PADDING_CHAR }) return false
            if (!code.endsWith(SEPARATOR)) return false
        }
        if (code.length - sep - 1 == 1) return false
        for (ch in code) {
            if (ch.uppercaseChar() !in CODE_ALPHABET && ch != SEPARATOR && ch != PADDING_CHAR) return false
        }
        return true
    }

    fun isShort(code: String): Boolean {
        if (!isValid(code)) return false
        val sep = code.indexOf(SEPARATOR)
        return sep in 0 until SEPARATOR_POSITION
    }

    fun isFull(code: String): Boolean {
        if (!isValid(code)) return false
        if (isShort(code)) return false
        val firstLatValue = CODE_ALPHABET.indexOf(code[0].uppercaseChar()) * ENCODING_BASE
        if (firstLatValue >= LATITUDE_MAX * 2) return false
        if (code.length > 1) {
            val firstLngValue = CODE_ALPHABET.indexOf(code[1].uppercaseChar()) * ENCODING_BASE
            if (firstLngValue >= LONGITUDE_MAX * 2) return false
        }
        return true
    }

    /** Decodes a full Plus Code into its bounding area. Returns null if [code] isn't a valid full code. */
    fun decode(code: String): CodeArea? {
        if (!isFull(code)) return null
        val clean = code.filter { it != SEPARATOR && it != PADDING_CHAR }.uppercase().take(MAX_DIGIT_COUNT)

        var normalLat = -LATITUDE_MAX * PAIR_PRECISION
        var normalLng = -LONGITUDE_MAX * PAIR_PRECISION
        var gridLat = 0.0
        var gridLng = 0.0

        val digits = min(clean.length, PAIR_CODE_LENGTH)
        var pv = PAIR_FIRST_PLACE_VALUE
        var i = 0
        while (i < digits) {
            normalLat += CODE_ALPHABET.indexOf(clean[i]) * pv
            normalLng += CODE_ALPHABET.indexOf(clean[i + 1]) * pv
            if (i < digits - 2) pv /= ENCODING_BASE
            i += 2
        }

        var latPrecision = pv.toDouble() / PAIR_PRECISION
        var lngPrecision = pv.toDouble() / PAIR_PRECISION

        if (clean.length > PAIR_CODE_LENGTH) {
            var rowpv = GRID_LAT_FIRST_PLACE_VALUE
            var colpv = GRID_LNG_FIRST_PLACE_VALUE
            val gridDigits = min(clean.length, MAX_DIGIT_COUNT)
            for (j in PAIR_CODE_LENGTH until gridDigits) {
                val digitVal = CODE_ALPHABET.indexOf(clean[j])
                val row = digitVal / GRID_COLUMNS
                val col = digitVal % GRID_COLUMNS
                gridLat += row * rowpv
                gridLng += col * colpv
                if (j < gridDigits - 1) {
                    rowpv /= GRID_ROWS
                    colpv /= GRID_COLUMNS
                }
            }
            latPrecision = rowpv.toDouble() / FINAL_LAT_PRECISION
            lngPrecision = colpv.toDouble() / FINAL_LNG_PRECISION
        }

        val lat = normalLat / PAIR_PRECISION + gridLat / FINAL_LAT_PRECISION
        val lng = normalLng / PAIR_PRECISION + gridLng / FINAL_LNG_PRECISION
        return CodeArea(lat, lng, lat + latPrecision, lng + lngPrecision)
    }

    /** Decodes a full code straight to its center point, or null if invalid. */
    fun decodeToLatLon(code: String): LatLon? = decode(code)?.let { LatLon(it.centerLat, it.centerLon) }

    private fun clipLatitude(lat: Double) = lat.coerceIn(-LATITUDE_MAX, LATITUDE_MAX)

    private fun normalizeLongitude(lon: Double): Double {
        var l = lon
        while (l < -LONGITUDE_MAX) l += 2 * LONGITUDE_MAX
        while (l >= LONGITUDE_MAX) l -= 2 * LONGITUDE_MAX
        return l
    }

    private fun locationToIntegers(lat: Double, lon: Double): Pair<Long, Long> {
        var latVal = floor(lat * FINAL_LAT_PRECISION).toLong() + (LATITUDE_MAX * FINAL_LAT_PRECISION).toLong()
        if (latVal < 0) latVal = 0
        val maxLatVal = (2 * LATITUDE_MAX * FINAL_LAT_PRECISION).toLong()
        if (latVal >= maxLatVal) latVal = maxLatVal - 1

        var lngVal = floor(lon * FINAL_LNG_PRECISION).toLong() + (LONGITUDE_MAX * FINAL_LNG_PRECISION).toLong()
        val maxLngVal = (2 * LONGITUDE_MAX * FINAL_LNG_PRECISION).toLong()
        lngVal = ((lngVal % maxLngVal) + maxLngVal) % maxLngVal
        return latVal to lngVal
    }

    private fun encodeIntegers(latValIn: Long, lngValIn: Long, codeLength: Int): String {
        var latVal = latValIn
        var lngVal = lngValIn
        val length = min(codeLength, MAX_DIGIT_COUNT)
        var code = ""

        if (length > PAIR_CODE_LENGTH) {
            repeat(MAX_DIGIT_COUNT - PAIR_CODE_LENGTH) {
                val latDigit = (latVal % GRID_ROWS).toInt()
                val lngDigit = (lngVal % GRID_COLUMNS).toInt()
                val ndx = latDigit * GRID_COLUMNS + lngDigit
                code = CODE_ALPHABET[ndx] + code
                latVal /= GRID_ROWS
                lngVal /= GRID_COLUMNS
            }
        } else {
            repeat(GRID_CODE_LENGTH) { latVal /= GRID_ROWS }
            repeat(GRID_CODE_LENGTH) { lngVal /= GRID_COLUMNS }
        }
        repeat(PAIR_CODE_LENGTH / 2) {
            code = CODE_ALPHABET[(lngVal % ENCODING_BASE).toInt()] + code
            code = CODE_ALPHABET[(latVal % ENCODING_BASE).toInt()] + code
            latVal /= ENCODING_BASE
            lngVal /= ENCODING_BASE
        }

        code = code.substring(0, SEPARATOR_POSITION) + SEPARATOR + code.substring(SEPARATOR_POSITION)
        return if (length >= SEPARATOR_POSITION) {
            code.substring(0, length + 1)
        } else {
            code.substring(0, length) + PADDING_CHAR.toString().repeat(SEPARATOR_POSITION - length) + SEPARATOR
        }
    }

    fun encode(lat: Double, lon: Double, codeLength: Int = PAIR_CODE_LENGTH): String {
        val (latInt, lngInt) = locationToIntegers(lat, lon)
        return encodeIntegers(latInt, lngInt, codeLength)
    }

    /**
     * Recovers a full code from a short one (e.g. "XJ5X+2R") using a nearby reference point
     * (current location, or a geocoded locality mentioned alongside the short code).
     */
    fun recoverNearest(code: String, referenceLat: Double, referenceLon: Double): String? {
        if (isFull(code)) return code.uppercase()
        if (!isShort(code)) return null
        val refLat = clipLatitude(referenceLat)
        val refLon = normalizeLongitude(referenceLon)
        val upper = code.uppercase()
        val paddingLength = SEPARATOR_POSITION - upper.indexOf(SEPARATOR)
        val resolution = Math.pow(20.0, 2 - paddingLength / 2.0)
        val halfResolution = resolution / 2.0

        val prefix = encode(refLat, refLon).substring(0, paddingLength)
        val combined = prefix + upper
        val codeLength = min(combined.count { it != SEPARATOR && it != PADDING_CHAR }, MAX_DIGIT_COUNT)
        var area = decode(combined) ?: return null
        var centerLat = area.centerLat
        var centerLon = area.centerLon

        if (refLat + halfResolution < centerLat && centerLat - resolution >= -LATITUDE_MAX) {
            centerLat -= resolution
        } else if (refLat - halfResolution > centerLat && centerLat + resolution <= LATITUDE_MAX) {
            centerLat += resolution
        }
        if (refLon + halfResolution < centerLon) {
            centerLon -= resolution
        } else if (refLon - halfResolution > centerLon) {
            centerLon += resolution
        }
        return encode(centerLat, centerLon, codeLength)
    }
}
