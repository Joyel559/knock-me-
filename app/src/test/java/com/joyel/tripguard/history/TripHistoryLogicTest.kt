package com.joyel.tripguard.history

import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.trip.Mode
import com.joyel.tripguard.trip.Trip
import com.joyel.tripguard.trip.TripState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TripHistoryLogicTest {
    private fun trip() = Trip(
        id = "t1",
        destLat = 12.0,
        destLon = 77.0,
        destLabel = "Office",
        radiusMeters = 3000,
        mode = Mode.CAB,
        finalAlarmEnabled = false,
        route = null,
        state = TripState.ACTIVE,
        startedAt = 1000L
    )

    @Test
    fun `start builds a row with null outcome and endedAt`() {
        val row = TripHistoryLogic.start(trip(), LatLon(11.0, 76.0), "Home", 1000L)
        assertEquals("t1", row.id)
        assertEquals("Office", row.toLabel)
        assertEquals("Home", row.fromLabel)
        assertNull(row.outcome)
        assertNull(row.endedAt)
        assertEquals(0.0, row.distanceMeters, 0.0)
    }

    @Test
    fun `onProgress updates distance and duration`() {
        val row = TripHistoryLogic.start(trip(), LatLon(11.0, 76.0), "Home", 1000L)
        val updated = TripHistoryLogic.onProgress(row, 500.0, 11000L)
        assertEquals(500.0, updated.distanceMeters, 0.0)
        assertEquals(10L, updated.durationSeconds)
    }

    @Test
    fun `onArrivalAlarm sets flag and timestamp once`() {
        val row = TripHistoryLogic.start(trip(), LatLon(11.0, 76.0), "Home", 1000L)
        val updated = TripHistoryLogic.onArrivalAlarm(row, 5000L)
        assertEquals(true, updated.arrivalAlarmRang)
        assertEquals(5000L, updated.arrivalAlarmAt)
    }

    @Test
    fun `finish sets outcome and endedAt`() {
        val row = TripHistoryLogic.start(trip(), LatLon(11.0, 76.0), "Home", 1000L)
        val finished = TripHistoryLogic.finish(row, 61000L, TripOutcome.ARRIVED)
        assertEquals(TripOutcome.ARRIVED, finished.outcome)
        assertEquals(61000L, finished.endedAt)
        assertEquals(60L, finished.durationSeconds)
    }

    @Test
    fun `routeAlarmEvent carries the user action and location`() {
        val event = TripHistoryLogic.routeAlarmEvent("t1", 2000L, LatLon(12.1, 77.1), 600.0, RouteAlarmAction.REROUTE)
        assertEquals("t1", event.tripId)
        assertEquals(RouteAlarmAction.REROUTE, event.userAction)
        assertEquals(600.0, event.offRouteMeters, 0.0)
        assertNotNull(event)
    }
}
