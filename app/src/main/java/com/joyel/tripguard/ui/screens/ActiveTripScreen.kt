package com.joyel.tripguard.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.service.ACTION_STOP
import com.joyel.tripguard.service.TripService
import com.joyel.tripguard.service.TripServiceHolder
import com.joyel.tripguard.ui.Routes
import com.joyel.tripguard.ui.components.TripMap

@Composable
fun ActiveTripScreen(navController: NavController) {
    val context = LocalContext.current
    val uiState by TripServiceHolder.state.collectAsState()
    var showStopConfirm by remember { mutableStateOf(false) }

    Scaffold(containerColor = Color.Black) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(24.dp)
        ) {
            val distanceKm = uiState.distanceMeters?.let { it / 1000.0 }
            Text(
                text = distanceKm?.let { "%.1f km".format(it) } ?: "—",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )
            Text(uiState.statusText, style = MaterialTheme.typography.titleMedium, color = Color.LightGray)
            Text("Accuracy: ${uiState.accuracyM?.let { "%.0f m".format(it) } ?: "—"}", color = Color.LightGray)
            Text("Interval: ${uiState.intervalMs?.let { "${it / 1000}s" } ?: "—"}", color = Color.LightGray)

            val trip = uiState.trip
            if (trip != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth().height(240.dp).padding(top = 16.dp)
                ) {
                    TripMap(
                        modifier = Modifier.fillMaxSize(),
                        center = LatLon(trip.destLat, trip.destLon),
                        destination = LatLon(trip.destLat, trip.destLon),
                        radiusMeters = trip.radiusMeters.toDouble(),
                        route = trip.routeLatLon
                    )
                }
            }

            Button(
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                onClick = { showStopConfirm = true }
            ) {
                Text("Stop Trip", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            containerColor = Color.Black,
            title = { Text("Stop trip?", color = Color.White) },
            text = { Text("This ends tracking and cancels any pending alarm.", color = Color.White) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    context.startService(Intent(context, TripService::class.java).setAction(ACTION_STOP))
                    navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                }) { Text("Stop", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text("Cancel", color = Color.White) }
            }
        )
    }
}
