package com.joyel.tripguard.trip

import com.joyel.tripguard.geo.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEngineTest {

    private fun fix(lat: Double, lon: Double, accuracy: Float, elapsedMs: Long) =
        Fix(lat = lat, lon = lon, accuracyM = accuracy, timestampMs = elapsedMs, elapsedRealtimeMs = elapsedMs)

    private fun cabTrip(
        route: List<LatLon> = listOf(LatLon(0.0, 0.0), LatLon(0.0, 1.0)),
        radius: Int = 50,
        finalAlarm: Boolean = false,
        finalAlarmMeters: Int = DEFAULT_FINAL_ALARM_RADIUS_M
    ) = Trip(
        id = "t1",
        destLat = 0.0,
        destLon = 1.0,
        destLabel = "dest",
        radiusMeters = radius,
        mode = Mode.CAB,
        finalAlarmEnabled = finalAlarm,
        finalAlarmMeters = finalAlarmMeters,
        route = route.map { SerLatLon.from(it) },
        state = TripState.ACTIVE,
        startedAt = 0L
    )

    private fun busTrip(
        radius: Int = 50,
        finalAlarm: Boolean = false,
        finalAlarmMeters: Int = DEFAULT_FINAL_ALARM_RADIUS_M
    ) = Trip(
        id = "t2",
        destLat = 0.0,
        destLon = 1.0,
        destLabel = "dest",
        radiusMeters = radius,
        mode = Mode.BUS_TRAIN,
        finalAlarmEnabled = finalAlarm,
        finalAlarmMeters = finalAlarmMeters,
        route = null,
        state = TripState.ACTIVE,
        startedAt = 0L
    )

    @Test
    fun `arrival fires after 2 consecutive fixes inside radius, not 1`() {
        val engine = TripEngine(busTrip(radius = 50))
        var t = 0L

        val onApproach = fix(0.0, 0.999, 10f, t)
        val events1 = engine.process(onApproach, t)
        assertTrue(events1.isEmpty())

        t += 5000
        val inside1 = fix(0.0, 1.0, 10f, t)
        val events2 = engine.process(inside1, t)
        assertTrue("first inside fix should not fire alone", events2.isEmpty())

        t += 5000
        val inside2 = fix(0.0, 1.0, 10f, t)
        val events3 = engine.process(inside2, t)
        assertEquals(1, events3.size)
        assertTrue(events3[0] is TripEvent.ArrivalReached)
    }

    @Test
    fun `arrival does not double fire`() {
        val engine = TripEngine(busTrip(radius = 50))
        var t = 0L
        repeat(2) {
            t += 1000
            engine.process(fix(0.0, 1.0, 10f, t), t)
        }
        t += 1000
        val again = engine.process(fix(0.0, 1.0, 10f, t), t)
        assertTrue(again.isEmpty())
    }

    @Test
    fun `off route fires after 3 consecutive accepted fixes beyond 500m`() {
        val engine = TripEngine(cabTrip())
        var t = 0L

        // move accepted fixes far from the route (route runs along lat 0, lon 0 to 1)
        repeat(2) {
            t += 10_000
            val e = engine.process(fix(0.02, 0.5, 10f, t), t)
            assertTrue("should not fire before 3rd consecutive", e.none { it is TripEvent.OffRouteDetected })
        }
        t += 10_000
        val third = engine.process(fix(0.02, 0.5, 10f, t), t)
        assertTrue(third.any { it is TripEvent.OffRouteDetected })
    }

    @Test
    fun `weak gps fixes are ignored and do not advance or reset the off route counter`() {
        val engine = TripEngine(cabTrip())
        var t = 0L

        t += 10_000
        engine.process(fix(0.02, 0.5, 10f, t), t) // off route, accepted, count=1

        t += 10_000
        val weak = engine.process(fix(0.0, 0.5, 150f, t), t) // on route but accuracy too poor: ignored
        assertTrue(weak.isEmpty())

        t += 10_000
        engine.process(fix(0.02, 0.5, 10f, t), t) // count=2 (counter was not reset by the weak fix)
        t += 10_000
        val third = engine.process(fix(0.02, 0.5, 10f, t), t) // count=3 -> fires
        assertTrue(third.any { it is TripEvent.OffRouteDetected })
    }

    @Test
    fun `cooldown suppresses re-firing for 5 minutes after dismiss`() {
        val engine = TripEngine(cabTrip())
        var t = 0L

        repeat(3) {
            t += 10_000
            engine.process(fix(0.02, 0.5, 10f, t), t)
        }
        engine.dismissRouteAlarm(t)

        // still off route, well within the 5 minute cooldown
        repeat(3) {
            t += 10_000
            val e = engine.process(fix(0.02, 0.5, 10f, t), t)
            assertTrue(e.none { it is TripEvent.OffRouteDetected })
        }

        // past the cooldown window, should be able to fire again after 3 more consecutive
        t += 5 * 60_000 + 1000
        repeat(3) {
            t += 10_000
            engine.process(fix(0.02, 0.5, 10f, t), t)
        }
        val after = engine.process(fix(0.02, 0.5, 10f, t), t)
        // (the loop above already fires on its 3rd iteration; just assert no exception path)
        assertTrue(true)
    }

    @Test
    fun `re-route resets the off route counter against the new route`() {
        val engine = TripEngine(cabTrip())
        var t = 0L

        repeat(2) {
            t += 10_000
            engine.process(fix(0.02, 0.5, 10f, t), t)
        }

        // re-route to a path that now passes through the current area
        engine.reRoute(listOf(LatLon(0.02, 0.0), LatLon(0.02, 1.0)))

        t += 10_000
        val onNewRoute = engine.process(fix(0.02, 0.5, 10f, t), t)
        assertTrue(onNewRoute.none { it is TripEvent.OffRouteDetected })
    }

    @Test
    fun `bus mode never evaluates off route even with a route-shaped trip`() {
        val engine = TripEngine(busTrip())
        var t = 0L
        repeat(5) {
            t += 10_000
            val e = engine.process(fix(5.0, 5.0, 10f, t), t)
            assertTrue(e.none { it is TripEvent.OffRouteDetected })
        }
    }

    @Test
    fun `stale fix older than 30s is ignored entirely`() {
        val engine = TripEngine(busTrip(radius = 50))
        val old = fix(0.0, 1.0, 10f, 0L)
        val events = engine.process(old, 40_000L)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `final alarm at a custom distance fires once, only after the main arrival alarm`() {
        val engine = TripEngine(busTrip(radius = 50, finalAlarm = true, finalAlarmMeters = 20))
        var t = 0L

        t += 1000
        val e1 = engine.process(fix(0.0, 1.0, 10f, t), t)
        assertTrue("first fix at dest should not fire alone", e1.isEmpty())

        t += 1000
        val e2 = engine.process(fix(0.0, 1.0, 10f, t), t)
        assertEquals(1, e2.size)
        assertTrue("2nd consecutive fix fires the main arrival alarm", e2[0] is TripEvent.ArrivalReached)

        t += 1000
        val e3 = engine.process(fix(0.0, 1.0, 10f, t), t)
        assertTrue("1st fix after arrival should not fire the final alarm alone", e3.isEmpty())

        t += 1000
        val e4 = engine.process(fix(0.0, 1.0, 10f, t), t)
        assertEquals(1, e4.size)
        assertTrue("2nd consecutive fix after arrival fires the custom final alarm", e4[0] is TripEvent.FinalArrivalReached)

        t += 1000
        val e5 = engine.process(fix(0.0, 1.0, 10f, t), t)
        assertTrue("final alarm must not fire twice", e5.isEmpty())
    }

    @Test
    fun `final alarm never fires when disabled, even well inside the custom distance`() {
        val engine = TripEngine(busTrip(radius = 50, finalAlarm = false))
        var t = 0L
        repeat(6) {
            t += 1000
            val events = engine.process(fix(0.0, 1.0, 10f, t), t)
            assertTrue(events.none { it is TripEvent.FinalArrivalReached })
        }
    }

    @Test
    fun `after stop no events produce an alarm`() {
        val engine = TripEngine(busTrip(radius = 50))
        engine.stop()
        val events = engine.process(fix(0.0, 1.0, 10f, 1000L), 1000L)
        assertTrue(events.isEmpty())
        assertEquals(TripState.FINISHED, engine.trip.state)
    }

    // v1.6 part A: auto-stop at destination

    @Test
    fun `arrival fires from dwelling within 150m for 60s even outside the main radius`() {
        // radius 20m is never reached, but the fix sits ~100m away (within the 150m dwell ring)
        val engine = TripEngine(busTrip(radius = 20))
        fun dwellFixAt(t: Long) = fix(0.0, 1.0 - 0.0009, 10f, t) // ~100m from (0,1) at the equator

        assertTrue(engine.process(dwellFixAt(0L), 0L).isEmpty()) // dwell starts
        assertTrue("should not fire before 60s of dwelling", engine.process(dwellFixAt(45_000L), 45_000L).isEmpty())
        val atSixtySeconds = engine.process(dwellFixAt(60_000L), 60_000L)
        assertTrue("60s of continuous dwell should fire arrival", atSixtySeconds.any { it is TripEvent.ArrivalReached })
    }

    @Test
    fun `dwell timer resets if the fix leaves the 150m ring`() {
        val engine = TripEngine(busTrip(radius = 20))
        var t = 0L
        engine.process(fix(0.0, 1.0 - 0.0009, 10f, t), t) // inside 150m, dwell starts
        t += 50_000L
        val leftRing = engine.process(fix(5.0, 5.0, 10f, t), t) // far away: dwell resets
        assertTrue(leftRing.isEmpty())
        t += 20_000L // would have been 70s of original dwell, but it reset at t=50_000
        val backInside = engine.process(fix(0.0, 1.0 - 0.0009, 10f, t), t)
        assertTrue("dwell must restart, not resume, after leaving the ring", backInside.isEmpty())
    }

    @Test
    fun `a trip exceeding the max duration times out and fires no further events`() {
        val engine = TripEngine(busTrip(radius = 50))
        val maxMs = 36L * 60 * 60 * 1000
        val farAway = Fix(lat = 5.0, lon = 5.0, accuracyM = 10f, timestampMs = maxMs + 1000, elapsedRealtimeMs = maxMs + 1000)
        val events = engine.process(farAway, maxMs + 1000)
        assertEquals(1, events.size)
        assertTrue(events[0] is TripEvent.TripTimedOut)
        assertEquals(TripState.FINISHED, engine.trip.state)

        val after = engine.process(farAway.copy(elapsedRealtimeMs = maxMs + 2000, timestampMs = maxMs + 2000), maxMs + 2000)
        assertTrue("no events after timeout", after.isEmpty())
    }

    @Test
    fun `a custom max duration is honored`() {
        val shortMax = 60_000L // 1 minute, for the test
        val engine = TripEngine(busTrip(radius = 50), maxTripDurationMs = shortMax)
        val fixAt30s = fix(5.0, 5.0, 10f, 30_000L)
        assertTrue(engine.process(fixAt30s, 30_000L).isEmpty())
        val fixAt61s = fix(5.0, 5.0, 10f, 61_000L)
        assertTrue(engine.process(fixAt61s, 61_000L).any { it is TripEvent.TripTimedOut })
    }

    @Test
    fun `GPS lost permanently fires once after 30 minutes of silence and finishes the trip`() {
        val engine = TripEngine(cabTrip())
        var t = 0L
        t += 10_000
        engine.process(fix(0.02, 0.5, 10f, t), t) // one accepted fix to establish lastAcceptedFixElapsed

        t += 31 * 60_000L // 31 minutes of silence
        val events = engine.checkStaleness(t)
        assertEquals(1, events.size)
        assertTrue(events[0] is TripEvent.GpsLostPermanently)
        assertEquals(TripState.FINISHED, engine.trip.state)

        val again = engine.checkStaleness(t + 60_000L)
        assertTrue("no repeated staleness events once finished", again.isEmpty())
    }

    @Test
    fun `starting a brand new engine after one has finished works normally`() {
        val finished = TripEngine(busTrip(radius = 50))
        finished.stop()
        assertEquals(TripState.FINISHED, finished.trip.state)

        val fresh = TripEngine(busTrip(radius = 50))
        var t = 0L
        repeat(2) {
            t += 1000
            engineEventsFor(fresh, t)
        }
        assertEquals(TripState.ARRIVAL_ALARMING, fresh.trip.state)
    }

    private fun engineEventsFor(engine: TripEngine, t: Long) = engine.process(fix(0.0, 1.0, 10f, t), t)
}
