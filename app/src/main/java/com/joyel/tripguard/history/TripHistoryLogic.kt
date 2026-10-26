package com.joyel.tripguard.history

import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.trip.Trip

/**
 * Pure (no Android deps) builders for [TripHistory]/[RouteAlarmEvent] rows from trip lifecycle
 * events, kept separate from [TripService][com.joyel.tripguard.service.TripService] so the
 * record-building logic is unit testable (PRD N6 tests).
 */
object TripHistoryLogic {
    fun start(trip: Trip, from: LatLon, fromLabel: String, now: Long): TripHistory = TripHistory(
        id = trip.id,
        startedAt = now,
        endedAt = null,
        fromLat = from.lat,
        fromLon = from.lon,
        fromLabel = fromLabel,
        toLat = trip.destLat,
        toLon = trip.destLon,
        toLabel = trip.destLabel,
        mode = trip.mode,
        radiusMeters = trip.radiusMeters,
        distanceMeters = 0.0,
        durationSeconds = 0,
        arrivalAlarmRang = false,
        arrivalAlarmAt = null,
        outcome = null
    )

    fun onProgress(current: TripHistory, distanceMeters: Double, now: Long): TripHistory = current.copy(
        distanceMeters = distanceMeters,
        durationSeconds = (now - current.startedAt) / 1000
    )

    fun onArrivalAlarm(current: TripHistory, now: Long): TripHistory = current.copy(
        arrivalAlarmRang = true,
        arrivalAlarmAt = now
    )

    fun finish(current: TripHistory, now: Long, outcome: TripOutcome): TripHistory = current.copy(
        endedAt = now,
        outcome = outcome,
        durationSeconds = (now - current.startedAt) / 1000
    )

    fun routeAlarmEvent(
        tripId: String,
        at: Long,
        location: LatLon,
        offRouteMeters: Double,
        action: RouteAlarmAction
    ): RouteAlarmEvent = RouteAlarmEvent(
        tripId = tripId,
        at = at,
        lat = location.lat,
        lon = location.lon,
        offRouteMeters = offRouteMeters,
        userAction = action
    )
}
