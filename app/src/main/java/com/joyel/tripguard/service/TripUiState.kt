package com.joyel.tripguard.service

import com.joyel.tripguard.trip.Trip
import kotlinx.coroutines.flow.MutableStateFlow

data class TripUiState(
    val trip: Trip? = null,
    val distanceMeters: Double? = null,
    val offRouteMeters: Double? = null,
    val accuracyM: Float? = null,
    val intervalMs: Long? = null,
    val statusText: String = "Idle"
)

/** Single-process holder the UI collects; the service is the only writer. */
object TripServiceHolder {
    val state = MutableStateFlow(TripUiState())
}
