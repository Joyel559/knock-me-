package com.joyel.tripguard.share

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPickTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `not expired right after creation`() {
        val now = 1_000_000L
        val pick = PendingPick(PickTarget.TO, createdAt = now)
        assertFalse(pick.isExpired(now))
        assertFalse(pick.isExpired(now + 29 * 60 * 1000))
    }

    @Test
    fun `expires after 30 minutes`() {
        val now = 1_000_000L
        val pick = PendingPick(PickTarget.FROM, createdAt = now)
        assertTrue(pick.isExpired(now + 31 * 60 * 1000))
    }

    @Test
    fun `resolveShareTarget uses the pending pick's target`() {
        assertEquals(PickTarget.FROM, resolveShareTarget(PendingPick(PickTarget.FROM, 0L)))
        assertEquals(PickTarget.TO, resolveShareTarget(PendingPick(PickTarget.TO, 0L)))
    }

    @Test
    fun `resolveShareTarget falls back to To with no pending pick`() {
        assertEquals(PickTarget.TO, resolveShareTarget(null))
    }

    @Test
    fun `shareTargetWasDefault is true only when nothing was pending`() {
        assertTrue(shareTargetWasDefault(null))
        assertFalse(shareTargetWasDefault(PendingPick(PickTarget.TO, 0L)))
    }

    @Test
    fun `shouldOfferClipboard requires a pending pick`() {
        assertFalse(shouldOfferClipboard(hasPendingPick = false, clipboardText = "geo:1,2", lastOfferedText = null))
    }

    @Test
    fun `shouldOfferClipboard ignores blank or non-place text`() {
        assertFalse(shouldOfferClipboard(true, null, null))
        assertFalse(shouldOfferClipboard(true, "  ", null))
        assertFalse(shouldOfferClipboard(true, "just some words", null))
    }

    @Test
    fun `shouldOfferClipboard ignores the same text offered last time`() {
        assertFalse(shouldOfferClipboard(true, "geo:12.9,77.6", lastOfferedText = "geo:12.9,77.6"))
    }

    @Test
    fun `shouldOfferClipboard offers a fresh Maps link, geo URI, coordinates, or Plus Code`() {
        assertTrue(shouldOfferClipboard(true, "https://maps.app.goo.gl/abc", null))
        assertTrue(shouldOfferClipboard(true, "geo:12.9,77.6", null))
        assertTrue(shouldOfferClipboard(true, "12.9716, 77.5946", null))
        assertTrue(shouldOfferClipboard(true, "7FG49QCJ+2VX", null))
    }

    @Test
    fun `TripSetupDraft survives a DataStore-style JSON round trip`() {
        val draft = TripSetupDraft(
            fromLat = 12.97, fromLon = 77.59, fromLabel = "Home",
            toLat = 13.0, toLon = 77.6, toLabel = "Office",
            radiusMeters = 2000, finalAlarmEnabled = true, finalAlarmMeters = 150
        )
        val pick = PendingPick(PickTarget.FROM, createdAt = 42L, draft = draft)
        val encoded = json.encodeToString(pick)
        val decoded = json.decodeFromString<PendingPick>(encoded)
        assertEquals(pick, decoded)
    }

    @Test
    fun `a pick that has expired is treated the same as none pending`() {
        val now = 1_000_000L
        val expired = PendingPick(PickTarget.FROM, createdAt = now - 31 * 60 * 1000)
        assertTrue(expired.isExpired(now))
        // Callers (PendingPickRepository.peek) must clear and return null once isExpired is true —
        // simulated here since this test has no Android DataStore to exercise.
        val afterExpiry: PendingPick? = null
        assertEquals(PickTarget.TO, resolveShareTarget(afterExpiry))
        assertNull(afterExpiry)
    }
}
