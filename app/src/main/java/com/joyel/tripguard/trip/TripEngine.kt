package com.joyel.tripguard.trip

import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.geo.haversineMeters
import com.joyel.tripguard.geo.pointToPolylineMeters

private const val STALE_FIX_MS = 30_000L
private const val ACCEPTED_ACCURACY_M = 100f
private const val ARRIVAL_CONSECUTIVE = 2
private const val GAP_THRESHOLD_MS = 2 * 60_000L
private const val GAP_SKIP_FIXES = 3 // the gap fix itself + 2 more
private const val SECONDARY_WINDOW_MS = 3 * 60_000L
private const val SECONDARY_OFF_ROUTE_M = 250.0
private const val ROUTE_COOLDOWN_MS = 5 * 60_000L
private const val ROUTE_IGNORE_MS = 10 * 60_000L
private const val GPS_WEAK_MS = 90_000L
private const val NO_FIX_TOO_LONG_MS = 5 * 60_000L
const val FINAL_ALARM_RADIUS_M = 300.0

/**
 * Pure logic: feed [Fix]es in, get [TripEvent]s out. No Android classes here so it is
 * trivially unit testable. One instance per active trip.
 */
class TripEngine(
    trip: Trip,
    private val offRouteThresholdM: Double = 500.0,
    private val offRouteConsecutiveRequired: Int = 3
) {
    var trip: Trip = trip
        private set

    private var route: List<LatLon>? = trip.routeLatLon

    private var arrivalConsecutive = 0
    private var arrivalFired = false
    private var finalConsecutive = 0
    private var finalFired = false

    private var offRouteConsecutive = 0
    private var lastAcceptedFixElapsed: Long? = null
    private var gapSkipRemaining = 0
    private var routeAlarmCooldownUntilElapsed = 0L
    private var routeAlarmIgnoredUntilElapsed = 0L
    private var gpsWeakAnnounced = false

    private val distanceHistory = ArrayDeque<Pair<Long, Double>>() // elapsedMs -> distanceToDest

    /**
     * Process a fix, where [nowElapsedMs] is the elapsed-realtime clock at the moment the
     * fix is handled (normally very close to [Fix.elapsedRealtimeMs]; kept separate so
     * tests can simulate a late-arriving/stale fix).
     */
    fun process(fix: Fix, nowElapsedMs: Long): List<TripEvent> {
        val events = mutableListOf<TripEvent>()
        if (trip.state == TripState.FINISHED) return events

        if (nowElapsedMs - fix.elapsedRealtimeMs > STALE_FIX_MS) {
            return events // stale, ignored entirely
        }

        val distanceToDest = haversineMeters(fix.latLon, trip.destination)

        // Arrival
        if (!arrivalFired) {
            if (distanceToDest <= trip.radiusMeters) {
                arrivalConsecutive++
            } else {
                arrivalConsecutive = 0
            }
            if (arrivalConsecutive >= ARRIVAL_CONSECUTIVE) {
                arrivalFired = true
                trip = trip.copy(state = TripState.ARRIVAL_ALARMING)
                events += TripEvent.ArrivalReached(distanceToDest)
            }
        } else if (trip.finalAlarmEnabled && !finalFired) {
            if (distanceToDest <= FINAL_ALARM_RADIUS_M) {
                finalConsecutive++
            } else {
                finalConsecutive = 0
            }
            if (finalConsecutive >= ARRIVAL_CONSECUTIVE) {
                finalFired = true
                events += TripEvent.FinalArrivalReached(distanceToDest)
            }
        }

        // Off-route (cab mode only, needs a route)
        val currentRoute = route
        if (trip.mode == Mode.CAB && currentRoute != null) {
            val accepted = fix.accuracyM <= ACCEPTED_ACCURACY_M
            if (accepted) {
                val last = lastAcceptedFixElapsed
                if (last != null && fix.elapsedRealtimeMs - last > GAP_THRESHOLD_MS) {
                    gapSkipRemaining = GAP_SKIP_FIXES
                }
                lastAcceptedFixElapsed = fix.elapsedRealtimeMs
                if (gpsWeakAnnounced) {
                    gpsWeakAnnounced = false
                    events += TripEvent.GpsRecovered
                }

                if (gapSkipRemaining > 0) {
                    gapSkipRemaining--
                } else {
                    val offRouteM = pointToPolylineMeters(fix.latLon, currentRoute)
                    if (offRouteM > offRouteThresholdM) {
                        offRouteConsecutive++
                    } else {
                        offRouteConsecutive = 0
                    }

                    distanceHistory.addLast(fix.elapsedRealtimeMs to distanceToDest)
                    while (distanceHistory.isNotEmpty() &&
                        fix.elapsedRealtimeMs - distanceHistory.first().first > SECONDARY_WINDOW_MS
                    ) {
                        distanceHistory.removeFirst()
                    }
                    val secondaryDrift = distanceHistory.size >= 2 &&
                        distanceHistory.last().second > distanceHistory.first().second &&
                        offRouteM > SECONDARY_OFF_ROUTE_M

                    val triggerable = offRouteConsecutive >= offRouteConsecutiveRequired || secondaryDrift
                    val suppressed = nowElapsedMs < routeAlarmCooldownUntilElapsed ||
                        nowElapsedMs < routeAlarmIgnoredUntilElapsed
                    val arrivalFiredThisCall = events.any { it is TripEvent.ArrivalReached }

                    if (triggerable && !suppressed && !arrivalFiredThisCall) {
                        offRouteConsecutive = 0
                        trip = trip.copy(state = TripState.ROUTE_ALARMING)
                        events += TripEvent.OffRouteDetected(offRouteM)
                    }
                }
            }
            // non-accepted fixes: ignored, counters untouched
        }

        return events
    }

    /** Call periodically (not tied to a fix) to detect a stalled GPS stream. */
    fun checkStaleness(nowElapsedMs: Long): List<TripEvent> {
        val last = lastAcceptedFixElapsed ?: return emptyList()
        val silentFor = nowElapsedMs - last
        val events = mutableListOf<TripEvent>()
        if (silentFor > NO_FIX_TOO_LONG_MS) {
            events += TripEvent.NoFixTooLong(silentFor)
        } else if (silentFor > GPS_WEAK_MS && !gpsWeakAnnounced) {
            gpsWeakAnnounced = true
            events += TripEvent.GpsWeak(silentFor)
        }
        return events
    }

    /** Backup trigger from the geofence, in case the fix-stream path is delayed. No-op if already fired. */
    fun triggerArrivalFromGeofence(): List<TripEvent> {
        if (arrivalFired) return emptyList()
        arrivalFired = true
        arrivalConsecutive = ARRIVAL_CONSECUTIVE
        trip = trip.copy(state = TripState.ARRIVAL_ALARMING)
        return listOf(TripEvent.ArrivalReached(trip.radiusMeters.toDouble()))
    }

    fun dismissArrival() {
        trip = trip.copy(
            state = if (!trip.finalAlarmEnabled) TripState.FINISHED else TripState.ACTIVE
        )
    }

    fun dismissFinalArrival() {
        trip = trip.copy(state = TripState.FINISHED)
    }

    fun dismissRouteAlarm(nowElapsedMs: Long) {
        routeAlarmCooldownUntilElapsed = nowElapsedMs + ROUTE_COOLDOWN_MS
        trip = trip.copy(state = TripState.ACTIVE)
    }

    fun ignoreRouteFor10Min(nowElapsedMs: Long) {
        routeAlarmIgnoredUntilElapsed = nowElapsedMs + ROUTE_IGNORE_MS
        trip = trip.copy(state = TripState.ACTIVE)
    }

    fun reRoute(newRoute: List<LatLon>) {
        route = newRoute
        trip = trip.copy(route = newRoute.map { SerLatLon.from(it) }, state = TripState.ACTIVE)
        offRouteConsecutive = 0
        distanceHistory.clear()
        routeAlarmCooldownUntilElapsed = 0L
        routeAlarmIgnoredUntilElapsed = 0L
    }

    fun stop() {
        trip = trip.copy(state = TripState.FINISHED)
    }
}
