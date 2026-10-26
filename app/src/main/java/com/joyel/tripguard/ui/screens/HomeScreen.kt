package com.joyel.tripguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.joyel.tripguard.history.HistoryRepository
import com.joyel.tripguard.history.SavedPlace
import com.joyel.tripguard.history.TripHistoryLogic
import com.joyel.tripguard.history.TripOutcome
import com.joyel.tripguard.location.LocationRepositoryHolder
import com.joyel.tripguard.trip.Trip
import com.joyel.tripguard.trip.TripPrefill
import com.joyel.tripguard.trip.TripPrefillHolder
import com.joyel.tripguard.trip.TripRepository
import com.joyel.tripguard.trip.TripState
import com.joyel.tripguard.ui.Routes
import com.joyel.tripguard.ui.components.LocationStatusSection
import com.joyel.tripguard.ui.permissions.PermCheckId
import com.joyel.tripguard.ui.permissions.buildPermissionChecklist

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    var lastTrip by remember { mutableStateOf<Trip?>(null) }
    val locationRepo = remember { LocationRepositoryHolder.get(context) }
    val locationState by locationRepo.state.collectAsState()
    LaunchedEffect(Unit) { locationRepo.start() }

    val historyRepo = remember { HistoryRepository(context) }
    val recentTrips by historyRepo.observeRecentTrips(3).collectAsState(initial = emptyList())
    val savedPlaces by historyRepo.observeSavedPlaces().collectAsState(initial = emptyList())

    var reminderDismissed by remember { mutableStateOf(false) }
    val missingReminder = remember(reminderDismissed) {
        if (reminderDismissed) null
        else buildPermissionChecklist(context).firstOrNull {
            !it.granted && it.id in setOf(PermCheckId.BACKGROUND_LOCATION, PermCheckId.BATTERY_OPTIMIZATION)
        }
    }

    LaunchedEffect(Unit) {
        val trip = TripRepository(context).getActiveTrip()
        lastTrip = trip
        val unfinished = historyRepo.getUnfinishedTrips()
        unfinished.forEach { row ->
            if (trip == null || trip.id != row.id || trip.state == TripState.FINISHED) {
                historyRepo.updateTrip(TripHistoryLogic.finish(row, System.currentTimeMillis(), TripOutcome.INTERRUPTED))
            }
        }
        if (trip != null && trip.state != TripState.FINISHED) {
            navController.navigate(Routes.ACTIVE) { popUpTo(Routes.HOME) { inclusive = true } }
        }
    }

    fun openWithPlace(place: SavedPlace) {
        TripPrefillHolder.pending = TripPrefill(
            location = com.joyel.tripguard.geo.LatLon(place.lat, place.lon),
            label = place.label,
            mode = com.joyel.tripguard.trip.Mode.CAB,
            radiusMeters = 3000
        )
        navController.navigate(Routes.SETUP)
    }

    Scaffold(containerColor = Color.Black) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text("Knock Me", style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            LocationStatusSection(locationState, modifier = Modifier.padding(top = 12.dp))

            Button(
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                onClick = { navController.navigate(Routes.SETUP) }
            ) {
                Text("New Trip", color = Color.White)
            }
            OutlinedButton(
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                onClick = { navController.navigate(Routes.HISTORY) }
            ) {
                Text("History", color = Color.White)
            }
            OutlinedButton(
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                onClick = { navController.navigate(Routes.SETTINGS) }
            ) {
                Text("Settings", color = Color.White)
            }

            missingReminder?.let { reminder ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Allow ${reminder.label.lowercase()} so alarms keep working",
                            color = Color.White,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                                onClick = { navController.navigate(Routes.ONBOARDING) }
                            ) {
                                Text("Fix", color = Color.White)
                            }
                            TextButton(onClick = { reminderDismissed = true }) {
                                Text("Dismiss", color = Color.Cyan)
                            }
                        }
                    }
                }
            }

            if (savedPlaces.isNotEmpty()) {
                Text("Saved places", color = Color.LightGray, modifier = Modifier.padding(top = 24.dp).fillMaxWidth())
                LazyRow(modifier = Modifier.padding(top = 8.dp)) {
                    items(savedPlaces, key = { it.id }) { place ->
                        FilterChip(
                            selected = false,
                            onClick = { openWithPlace(place) },
                            label = { Text(place.name, color = Color.White) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            if (recentTrips.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Recent trips", color = Color.LightGray)
                    TextButton(onClick = { navController.navigate(Routes.HISTORY) }) { Text("See all", color = Color.Cyan) }
                }
                recentTrips.forEach { trip ->
                    Text(
                        "${trip.toLabel} — ${trip.outcome?.name?.lowercase() ?: "in progress"}",
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
