package com.joyel.tripguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.joyel.tripguard.history.HistoryRepository
import com.joyel.tripguard.history.TripHistoryLogic
import com.joyel.tripguard.history.TripOutcome
import com.joyel.tripguard.trip.TripRepository
import com.joyel.tripguard.trip.TripState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fired every ~4 minutes (via AlarmManager.setAlarmClock, see [WatchdogScheduler]) while a trip
 * is active. If the trip's last heartbeat is stale, rings a loud "Tracking stopped" alert and
 * marks the trip history row INTERRUPTED; the live TripRepository entry is left alone so the
 * trip can still resume normally when the app is reopened. Reschedules itself unless the trip
 * has stopped/finished.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tripRepo = TripRepository(appContext)
                val trip = tripRepo.getActiveTrip()
                if (trip == null || trip.state == TripState.FINISHED) {
                    return@launch // trip stopped/finished: chain ends, don't reschedule
                }
                // Safety cap backstop: if the service already died, its own per-fix TripEngine
                // check can't fire, so the watchdog is what actually ends an over-long trip.
                val maxHours = com.joyel.tripguard.trip.SettingsRepository(appContext).get().maxTripHours
                if (System.currentTimeMillis() - trip.startedAt >= maxHours * 60L * 60 * 1000) {
                    finishTimedOutTrip(appContext, maxHours)
                    return@launch
                }

                val heartbeatRepo = HeartbeatRepository(appContext)
                val now = SystemClock.elapsedRealtime()
                val lastHeartbeat = heartbeatRepo.getLastHeartbeatElapsedMs()
                val lastRung = heartbeatRepo.getLastRingElapsedMs()

                if (WatchdogLogic.shouldRing(now, lastHeartbeat, tripActive = true, lastRung)) {
                    heartbeatRepo.recordRing(now)
                    markUnfinishedTripsInterrupted(appContext)
                    AlarmController.raiseLoudAlarm(
                        appContext,
                        AlarmKind.TRACKING_STOPPED,
                        "Knock Me stopped tracking",
                        "Tap to resume your trip"
                    )
                }
                WatchdogScheduler.scheduleNext(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun finishTimedOutTrip(context: Context, maxHours: Int) {
        TripRepository(context).saveActiveTrip(null)
        val historyRepo = HistoryRepository(context)
        historyRepo.getUnfinishedTrips().forEach { row ->
            historyRepo.updateTrip(TripHistoryLogic.finish(row, System.currentTimeMillis(), TripOutcome.TIMED_OUT))
        }
        com.joyel.tripguard.history.MemoryMirror.requestWrite(context)
        AlarmController.raiseShortAlert(context, "Trip ended", "Trip ended after $maxHours hours")
        // Courtesy stop in case the service is still alive; harmless if it already died.
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                context, Intent(context, TripService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private suspend fun markUnfinishedTripsInterrupted(context: Context) {
        val historyRepo = HistoryRepository(context)
        historyRepo.getUnfinishedTrips().forEach { row ->
            historyRepo.updateTrip(TripHistoryLogic.finish(row, System.currentTimeMillis(), TripOutcome.INTERRUPTED))
        }
        com.joyel.tripguard.trip.ReliabilityRepository(context).markInterruptionDetected()
        com.joyel.tripguard.history.MemoryMirror.requestWrite(context)
    }
}
