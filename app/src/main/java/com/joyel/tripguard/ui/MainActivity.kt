package com.joyel.tripguard.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.joyel.tripguard.share.ShareIntentParser
import com.joyel.tripguard.share.SharedDestinationHolder
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

object Routes {
    const val HOME = "home"
    const val SETUP = "setup"
    const val ACTIVE = "active"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history_detail"
    const val SAVED_PLACES = "saved_places"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val onboardingDone = runBlocking { OnboardingRepository(this@MainActivity).isComplete() }
        val sharedText = extractShareText(intent)
        val startDestination = when {
            !onboardingDone -> Routes.ONBOARDING
            sharedText != null -> Routes.SETUP
            else -> Routes.HOME
        }
        if (sharedText != null && onboardingDone) {
            SharedDestinationHolder.resolving = true
            lifecycleScope.launch {
                val result = ShareIntentParser().parse(sharedText)
                SharedDestinationHolder.pending = result
                SharedDestinationHolder.parseFailed = result == null
                SharedDestinationHolder.resolving = false
            }
        }

        setContent {
            TripGuardTheme {
                val navController = rememberNavController()
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
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(onFinished = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                                }
                            }
                        })
                    }
                }
            }
        }
    }

    private fun extractShareText(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_VIEW -> intent.dataString
        else -> null
    }
}
