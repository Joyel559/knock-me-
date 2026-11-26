package com.joyel.tripguard

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.joyel.tripguard.share.PickTarget
import com.joyel.tripguard.share.SharedDestinationHolder
import com.joyel.tripguard.share.TripSetupTestState
import com.joyel.tripguard.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches [MainActivity] with an incoming ACTION_VIEW Google Maps link and an ACTION_SEND text
 * share, and checks the destination lands in [TripSetupTestState] — the resolve outcome
 * `PlaceInputResolver` produces inside `TripSetupScreen`'s own Compose state (v2.0 moved
 * resolution out of a static holder, so there's no raw URL ever visible anywhere to assert on —
 * by construction, since the field is filled from `Place.name`, never from the input text). There
 * is also no map anywhere in the app to accidentally duplicate (v1.8 removed the last one), so
 * "exactly one root content" is structural, not something this test needs to probe for.
 *
 * Could not be run in this (no-device) environment — see docs/TEST-PLAN-SHARE.md. Run it with
 * `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class ShareToTripSetupTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun resetState() {
        SharedDestinationHolder.rawText = null
        SharedDestinationHolder.target = PickTarget.TO
        SharedDestinationHolder.targetWasDefault = false
        TripSetupTestState.lastAppliedFrom = null
        TripSetupTestState.lastAppliedTo = null
    }

    @Test
    fun actionView_googleMapsPlaceUrl_fillsTheToField() = runBlocking {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(
                "https://www.google.com/maps/place/Cubbon+Park/@12.9763,77.5929,17z/data=!3d12.9763!4d77.5929"
            )
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            val place = waitForAppliedTo()
            assertNotNull("expected a resolved place for the Maps place URL", place)
            assertEquals(12.9763, place!!.lat, 0.001)
            assertEquals("Cubbon Park", place.name)
        }
    }

    @Test
    fun actionSend_plainTextShare_fillsTheToField() = runBlocking {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "12.9716, 77.5946")
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            val place = waitForAppliedTo()
            assertNotNull("expected a resolved place for the plain-text coordinates share", place)
            assertEquals(12.9716, place!!.lat, 0.001)
        }
    }

    private suspend fun waitForAppliedTo(): com.joyel.tripguard.share.Place? {
        var waited = 0L
        while (TripSetupTestState.lastAppliedTo == null && waited < 15_000L) {
            kotlinx.coroutines.delay(200)
            waited += 200
        }
        return TripSetupTestState.lastAppliedTo
    }
}
