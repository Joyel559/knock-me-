package com.joyel.tripguard.trip

import com.joyel.tripguard.geo.LatLon
import kotlinx.serialization.Serializable

enum class Mode { CAB, BUS_TRAIN }

enum class TripState { IDLE, ACTIVE, ARRIVAL_ALARMING, ROUTE_ALARMING, FINISHED }

@Serializable
data class SerLatLon(val lat: Double, val lon: Double) {
    fun toLatLon() = LatLon(lat, lon)
    companion object {
        fun from(l: LatLon) = SerLatLon(l.lat, l.lon)
    }
}

@Serializable
data class Trip(
    val id: String,
    val destLat: Double,
    val destLon: Double,
    val destLabel: String,
    val radiusMeters: Int,
    val mode: Mode,
    val finalAlarmEnabled: Boolean,
    val route: List<SerLatLon>?,
    val state: TripState,
    val startedAt: Long
) {
    val destination: LatLon get() = LatLon(destLat, destLon)
    val routeLatLon: List<LatLon>? get() = route?.map { it.toLatLon() }
}

/** A single accepted/raw location reading fed into [TripEngine]. */
data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val timestampMs: Long,
    val elapsedRealtimeMs: Long
) {
    val latLon: LatLon get() = LatLon(lat, lon)
}

sealed class TripEvent {
    data class ArrivalReached(val distanceM: Double) : TripEvent()
    data class FinalArrivalReached(val distanceM: Double) : TripEvent()
    data class OffRouteDetected(val offRouteM: Double) : TripEvent()
    data class GpsWeak(val silentForMs: Long) : TripEvent()
    object GpsRecovered : TripEvent()
    data class NoFixTooLong(val silentForMs: Long) : TripEvent()
}
