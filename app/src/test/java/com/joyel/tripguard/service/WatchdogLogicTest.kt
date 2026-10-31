package com.joyel.tripguard.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchdogLogicTest {

    @Test
    fun `fresh heartbeat never rings`() {
        val now = 10 * 60_000L
        val lastHeartbeat = now - 60_000L // 1 minute ago, well within the 3-minute staleness window
        assertFalse(WatchdogLogic.shouldRing(now, lastHeartbeat, tripActive = true, lastRungElapsedMs = null))
    }

    @Test
    fun `stale heartbeat rings when trip is active and never rung before`() {
        val now = 10 * 60_000L
        val lastHeartbeat = now - 4 * 60_000L // 4 minutes ago, past the 3-minute threshold
        assertTrue(WatchdogLogic.shouldRing(now, lastHeartbeat, tripActive = true, lastRungElapsedMs = null))
    }

    @Test
    fun `never rings when the trip is not active, even with a very stale heartbeat`() {
        val now = 60 * 60_000L
        val lastHeartbeat = now - 30 * 60_000L
        assertFalse(WatchdogLogic.shouldRing(now, lastHeartbeat, tripActive = false, lastRungElapsedMs = null))
    }

    @Test
    fun `never rings before any heartbeat has ever been recorded`() {
        val now = 60_000L
        assertFalse(WatchdogLogic.shouldRing(now, lastHeartbeatElapsedMs = null, tripActive = true, lastRungElapsedMs = null))
    }

    @Test
    fun `cooldown suppresses re-ringing within 10 minutes of the last ring`() {
        val now = 20 * 60_000L
        val lastHeartbeat = now - 5 * 60_000L // stale
        val lastRung = now - 5 * 60_000L // rang 5 minutes ago, inside the 10-minute cooldown
        assertFalse(WatchdogLogic.shouldRing(now, lastHeartbeat, tripActive = true, lastRungElapsedMs = lastRung))
    }

    @Test
    fun `rings again once the 10-minute cooldown has passed and it is still stale`() {
        val now = 30 * 60_000L
        val lastHeartbeat = now - 15 * 60_000L // still stale the whole time
        val lastRung = now - 11 * 60_000L // just past the 10-minute cooldown
        assertTrue(WatchdogLogic.shouldRing(now, lastHeartbeat, tripActive = true, lastRungElapsedMs = lastRung))
    }

    @Test
    fun `checks every 4 minutes by definition`() {
        assertTrue(WatchdogLogic.CHECK_INTERVAL_MS == 4 * 60_000L)
    }
}
