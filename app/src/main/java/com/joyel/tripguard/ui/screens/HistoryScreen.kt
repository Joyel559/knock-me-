package com.joyel.tripguard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.joyel.tripguard.history.HistoryRepository
import com.joyel.tripguard.history.RouteAlarmEvent
import com.joyel.tripguard.history.TripHistory
import com.joyel.tripguard.history.SavedPlace
import com.joyel.tripguard.trip.Mode
import com.joyel.tripguard.trip.TripPrefill
import com.joyel.tripguard.trip.TripPrefillHolder
import com.joyel.tripguard.ui.Routes
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private fun formatWhen(ms: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms)) + " " +
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val repo = remember { HistoryRepository(context) }
    val trips by repo.observeTrips().collectAsState(initial = emptyList())
    var alarmCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(trips) {
        alarmCounts = trips.associate { it.id to repo.countRouteAlarmEvents(it.id) }
    }

    Scaffold { padding ->
        if (trips.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("No trips yet")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(trips, key = { it.id }) { trip ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable { navController.navigate("${Routes.HISTORY_DETAIL}/${trip.id}") }
                    ) {
                        Text("${trip.fromLabel} -> ${trip.toLabel}")
                        Text(formatWhen(trip.startedAt))
                        Text("${if (trip.mode == Mode.CAB) "Cab" else "Bus/Train"} · ${trip.outcome?.name?.lowercase() ?: "in progress"} · ${alarmCounts[trip.id] ?: 0} wrong-route alarms")
                    }
                    Divider()
                }
            }
        }
    }
}

@Composable
fun HistoryDetailScreen(navController: NavController, tripId: String) {
    val context = LocalContext.current
    val repo = remember { HistoryRepository(context) }
    val scope = rememberCoroutineScope()
    var trip by remember { mutableStateOf<TripHistory?>(null) }
    var events by remember { mutableStateOf<List<RouteAlarmEvent>>(emptyList()) }

    LaunchedEffect(tripId) {
        trip = repo.getTrip(tripId)
        events = repo.getRouteAlarmEvents(tripId)
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            trip?.let { t ->
                Text("${t.fromLabel} -> ${t.toLabel}")
                Text(formatWhen(t.startedAt))
                Text("Mode: ${if (t.mode == Mode.CAB) "Cab" else "Bus/Train"}")
                Text("Outcome: ${t.outcome?.name?.lowercase() ?: "in progress"}")
                Text("Distance travelled: %.1f km".format(t.distanceMeters / 1000.0))
                Text("Duration: ${t.durationSeconds / 60} min")

                Text("Wrong-route alarms (${events.size})", modifier = Modifier.padding(top = 16.dp))
                events.forEach { e ->
                    Text("${formatWhen(e.at)} — %.0f m off, ${e.userAction.name.lowercase()}".format(e.offRouteMeters))
                }

                Button(
                    modifier = Modifier.padding(top = 24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                    onClick = {
                        TripPrefillHolder.pending = TripPrefill(
                            location = t.destination,
                            label = t.toLabel,
                            mode = t.mode,
                            radiusMeters = t.radiusMeters
                        )
                        navController.navigate(Routes.SETUP)
                    }
                ) { Text("Repeat trip", color = Color.White) }

                OutlinedButton(
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        scope.launch {
                            repo.insertSavedPlace(
                                SavedPlace(
                                    name = t.toLabel,
                                    label = t.toLabel,
                                    lat = t.toLat,
                                    lon = t.toLon,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                ) { Text("Save destination as a place") }

                TextButton(
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        scope.launch {
                            repo.deleteTrip(tripId)
                            navController.popBackStack()
                        }
                    }
                ) { Text("Delete trip") }
            }
        }
    }
}

private val TripHistory.destination get() = com.joyel.tripguard.geo.LatLon(toLat, toLon)
