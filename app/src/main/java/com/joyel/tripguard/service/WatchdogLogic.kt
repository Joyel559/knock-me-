package com.joyel.tripguard.service

/**
 * Pure decision logic for the dead-man watchdog (no Android deps, unit tested). A fix's
 * elapsedRealtime heartbeat is written at most once per 30s while a trip is active; this fires
 * off an AlarmManager.setAlarmClock chain every [CHECK_INTERVAL_MS] to check it's still fresh.
 */
object WatchdogLogic {
    const val STALE_AFTER_MS = 3 * 60_000L
    const val RING_COOLDOWN_MS = 10 * 60_000L
    const val CHECK_INTERVAL_MS = 4 * 60_000L

    /**
     * Should the "Tracking stopped" alert ring right now?
     * - Never if the trip isn't active (nothing to watch).
     * - Never if no heartbeat has been recorded yet (trip just started, hasn't had a fix yet).
     * - Never if the heartbeat is still fresh (within [STALE_AFTER_MS]).
     * - Never more than once per [RING_COOLDOWN_MS], even if still stale on the next check.
     */
    fun shouldRing(
        nowElapsedMs: Long,
        lastHeartbeatElapsedMs: Long?,
        tripActive: Boolean,
        lastRungElapsedMs: Long?
    ): Boolean {
        if (!tripActive) return false
        if (lastHeartbeatElapsedMs == null) return false
        val stale = nowElapsedMs - lastHeartbeatElapsedMs > STALE_AFTER_MS
        if (!stale) return false
        if (lastRungElapsedMs != null && nowElapsedMs - lastRungElapsedMs < RING_COOLDOWN_MS) return false
        return true
    }
}
