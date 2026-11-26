package com.joyel.tripguard.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.joyel.tripguard.BuildConfig
import com.joyel.tripguard.share.PendingPickRepository
import com.joyel.tripguard.share.SharedDestinationHolder
import com.joyel.tripguard.share.resolveShareTarget
import com.joyel.tripguard.share.shareTargetWasDefault
import com.joyel.tripguard.trip.TripRepository
import com.joyel.tripguard.trip.TripState
import com.joyel.tripguard.service.ACTION_STOP
import com.joyel.tripguard.service.TripService
import com.joyel.tripguard.ui.onboarding.OnboardingRepository
import com.joyel.tripguard.ui.onboarding.OnboardingScreen
import com.joyel.tripguard.ui.screens.ActiveTripScreen
import com.joyel.tripguard.ui.screens.HistoryDetailScreen
import com.joyel.tripguard.ui.screens.HistoryScreen
import com.joyel.tripguard.ui.screens.HomeScreen
import com.joyel.tripguard.ui.screens.SavedPlacesScreen
import com.joyel.tripguard.ui.screens.SettingsScreen
import com.joyel.tripguard.ui.screens.TripSetupScreen
import com.joyel.tripguard.ui.theme.TripGuardTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val SHARE_LOG_TAG = "KnockMeShare"

/** Pure decision used by [MainActivity] when a share arrives: a running trip must be confirmed
 * before it's replaced. Kept pure/top-level so it's unit-testable without an Activity. */
fun shouldPromptReplaceTrip(activeTripState: TripState?): Boolean =
    activeTripState != null && activeTripState != TripState.IDLE && activeTripState != TripState.FINISHED

object Routes {
    const val HOME = "home"
    const val SETUP = "setup"
    const val ACTIVE = "active"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history_detail"
    const val SAVED_PLACES = "saved_places"
    const val RELIABILITY = "reliability"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
    const val LINK_DEBUG = "link_debug"
}

class MainActivity : ComponentActivity() {

    private var navControllerRef: NavController? = null
    /** Holds a raw share that arrived while a trip is already ACTIVE, until the user picks
     * Replace or Keep current trip in the dialog shown from [setContent]. */
    private val activeTripConflictState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val onboardingDone = runBlocking { OnboardingRepository(this@MainActivity).isComplete() }
        // Bug fix: location permission must be asked on every launch while it's missing, not
        // only before onboarding has ever completed once (it may have been revoked since).
        val locationGranted = com.joyel.tripguard.ui.permissions.hasLocationPermission(this)
        val needsOnboarding = !onboardingDone || !locationGranted
        val sharedText = extractShareText(intent)
        val startDestination = when {
            needsOnboarding -> Routes.ONBOARDING
            sharedText != null -> Routes.SETUP
            else -> Routes.HOME
        }
        // Capture the share even while onboarding is required: handleIncomingIntent stashes it
        // in SharedDestinationHolder regardless, so it's picked up by TripSetupScreen once
        // onboarding finishes and navigates there (see the ONBOARDING composable below). Never
        // lose the shared place because permission/GPS wasn't ready yet.
        if (sharedText != null) {
            handleIncomingIntent(sharedText, navigateIfAlreadyOpen = false)
        }

        setContent {
            TripGuardTheme {
                val navController = rememberNavController()
                navControllerRef = navController
                val conflictText = activeTripConflictState.value
                NavHost(navController = navController, startDestination = startDestination) {
                    composable(Routes.HOME) { HomeScreen(navController) }
                    composable(Routes.SETUP) { TripSetupScreen(navController) }
                    composable(Routes.ACTIVE) { ActiveTripScreen(navController) }
                    composable(Routes.SETTINGS) { SettingsScreen(navController) }
                    composable(Routes.HISTORY) { HistoryScreen(navController) }
                    composable("${Routes.HISTORY_DETAIL}/{tripId}") { backStackEntry ->
                        val tripId = backStackEntry.arguments?.getString("tripId")
                        if (tripId != null) HistoryDetailScreen(navController, tripId)
                    }
                    composable(Routes.SAVED_PLACES) { SavedPlacesScreen(navController) }
                    composable(Routes.RELIABILITY) { com.joyel.tripguard.ui.screens.ReliabilityScreen(navController) }
                    composable(Routes.ABOUT) { com.joyel.tripguard.ui.screens.AboutScreen(navController) }
                    composable(Routes.PRIVACY) { com.joyel.tripguard.ui.screens.PrivacyScreen(navController) }
                    composable(Routes.LINK_DEBUG) { com.joyel.tripguard.ui.screens.LinkDebugScreen(navController) }
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(onFinished = {
                            val target = if (SharedDestinationHolder.rawText != null) Routes.SETUP else null
                            if (target != null) {
                                navController.navigate(target) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                            } else if (!navController.popBackStack()) {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                                }
                            }
                        })
                    }
                }
                if (conflictText != null) {
                    AlertDialog(
                        onDismissRequest = { activeTripConflictState.value = null },
                        containerColor = Color.Black,
                        title = { Text("A trip is running", color = Color.White) },
                        text = { Text("Replace it with this destination?", color = Color.LightGray) },
                        confirmButton = {
                            TextButton(onClick = {
                                activeTripConflictState.value = null
                                startService(Intent(this, TripService::class.java).setAction(ACTION_STOP))
                                resolveAndGoToSetup(conflictText)
                            }) { Text("Replace", color = Color.White) }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeTripConflictState.value = null }) {
                                Text("Keep current trip", color = Color.White)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val sharedText = extractShareText(intent) ?: return
        handleIncomingIntent(sharedText, navigateIfAlreadyOpen = true)
    }

    /** Single entry point for both cold start and an already-running app receiving a new share. */
    private fun handleIncomingIntent(sharedText: String, navigateIfAlreadyOpen: Boolean) {
        if (BuildConfig.DEBUG) {
            Log.d(SHARE_LOG_TAG, "action=${intent.action} data=${intent.dataString} extraText=${intent.getStringExtra(Intent.EXTRA_TEXT)}")
        }
        lifecycleScope.launch {
            val activeTrip = runCatching { TripRepository(this@MainActivity).getActiveTrip() }.getOrNull()
            if (shouldPromptReplaceTrip(activeTrip?.state)) {
                activeTripConflictState.value = sharedText
                return@launch
            }
            resolveAndGoToSetup(sharedText, navigateIfAlreadyOpen)
        }
    }

    private fun resolveAndGoToSetup(sharedText: String, navigateIfAlreadyOpen: Boolean = true) {
        SharedDestinationHolder.rawText = sharedText
        if (navigateIfAlreadyOpen) {
            navControllerRef?.navigate(Routes.SETUP)
        }
        lifecycleScope.launch {
            // Peek only — Trip Setup itself owns clearing the pending pick once it has safely
            // applied the pick's draft, so a race between this and its own mount effect can't
            // lose the draft. The actual place resolution (PlaceInputResolver) runs inside
            // TripSetupScreen, which has a current-location fix to bias it with.
            val pick = runCatching { PendingPickRepository(this@MainActivity).peek() }.getOrNull()
            SharedDestinationHolder.target = resolveShareTarget(pick)
            SharedDestinationHolder.targetWasDefault = shareTargetWasDefault(pick)
        }
    }

    private fun extractShareText(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_VIEW -> intent.dataString
        else -> null
    }
}
