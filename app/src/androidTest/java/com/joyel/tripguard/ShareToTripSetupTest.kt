package com.joyel.tripguard

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.joyel.tripguard.share.PickTarget
import com.joyel.tripguard.share.SharedDestinationHolder
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
 * share, and checks the destination lands in [SharedDestinationHolder] for Trip Setup to pick up
 * (the same holder TripSetupScreen reads, see its "resolving" poll loop). Asserts on that holder
 * rather than Compose UI, since this project has no Espresso/Compose-test dependency (see
 * docs/TEST-PLAN-SHARE.md for why, and the manual adb steps this complements).
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
    fun resetHolder() {
        SharedDestinationHolder.pending = null
        SharedDestinationHolder.parseFailed = false
        SharedDestinationHolder.resolving = false
        SharedDestinationHolder.rawText = null
        SharedDestinationHolder.target = PickTarget.TO
        SharedDestinationHolder.targetWasDefault = false
    }

    @Test
    fun actionView_googleMapsPlaceUrl_fillsSharedDestination() = runBlocking {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(
                "https://www.google.com/maps/place/Cubbon+Park/@12.9763,77.5929,17z/data=!3d12.9763!4d77.5929"
            )
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            waitForResolve()
            val pending = SharedDestinationHolder.pending
            assertNotNull("expected a parsed destination for the Maps place URL", pending)
            assertEquals(12.9763, pending!!.location.lat, 0.001)
            assertEquals("Cubbon Park", pending.label)
        }
    }

    @Test
    fun actionSend_plainTextShare_fillsSharedDestination() = runBlocking {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "12.9716, 77.5946")
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            waitForResolve()
            val pending = SharedDestinationHolder.pending
            assertNotNull("expected a parsed destination for the plain-text coordinates share", pending)
            assertEquals(12.9716, pending!!.location.lat, 0.001)
        }
    }

    private suspend fun waitForResolve() {
        var waited = 0L
        while (SharedDestinationHolder.resolving && waited < 15_000L) {
            kotlinx.coroutines.delay(200)
            waited += 200
        }
    }
}
