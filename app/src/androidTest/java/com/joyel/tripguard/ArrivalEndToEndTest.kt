package com.joyel.tripguard

import android.content.Intent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.joyel.tripguard.history.HistoryRepository
import com.joyel.tripguard.history.TripOutcome
import com.joyel.tripguard.service.ACTION_START
import com.joyel.tripguard.service.TripService
import com.joyel.tripguard.trip.Mode
import com.joyel.tripguard.trip.Trip
import com.joyel.tripguard.trip.TripRepository
import com.joyel.tripguard.trip.TripState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Starts a trip to a destination ~100m away, lets the emulator's mock location reach it, and
 * checks the trip actually ends and a history row records it as arrived.
 *
 * Needs a running emulator/device with a mock location provider set (e.g. via
 * `adb emu geo fix <lon> <lat>` driven from outside the test, or Android Studio's Extended
 * Controls). Kept intentionally short per docs/TEST-PLAN.md's manual checklist, which this
 * mirrors; could not be run in this (no-device) environment — see docs/TEST-PLAN.md.
 */
@RunWith(AndroidJUnit4::class)
class ArrivalEndToEndTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    @get:Rule
    val activityRule = ActivityScenarioRule(com.joyel.tripguard.ui.MainActivity::class.java)

    @Test
    fun arrivingAtDestination_endsTripAndWritesHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Destination ~100m north of Bangalore's reference point used elsewhere in this project.
        val trip = Trip(
            id = UUID.randomUUID().toString(),
            destLat = 12.9725,
            destLon = 77.5946,
            destLabel = "Test destination",
            radiusMeters = 100,
            mode = Mode.BUS_TRAIN,
            finalAlarmEnabled = false,
            route = null,
            state = TripState.ACTIVE,
            startedAt = System.currentTimeMillis()
        )
        TripRepository(context).saveActiveTrip(trip)
        context.startForegroundService(Intent(context, TripService::class.java).setAction(ACTION_START))

        // The emulator must be fed mock fixes approaching destLat/destLon externally (adb emu geo
        // fix) while this runs. Poll history for up to 2 minutes for the row to turn ARRIVED.
        val historyRepo = HistoryRepository(context)
        var row = historyRepo.getTrip(trip.id)
        var waited = 0L
        while (row?.outcome != TripOutcome.ARRIVED && waited < 120_000L) {
            kotlinx.coroutines.delay(2000)
            waited += 2000
            row = historyRepo.getTrip(trip.id)
        }

        assertNotNull("expected a history row for the trip", row)
        assertEquals(TripOutcome.ARRIVED, row?.outcome)
        assertEquals(null, TripRepository(context).getActiveTrip())
    }
}
