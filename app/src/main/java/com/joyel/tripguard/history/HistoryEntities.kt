package com.joyel.tripguard.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.joyel.tripguard.trip.Mode

enum class TripOutcome { ARRIVED, STOPPED_EARLY, GPS_LOST, INTERRUPTED }

enum class RouteAlarmAction { REROUTE, IGNORE, STOP, TIMED_OUT }

@Entity(tableName = "trip_history")
data class TripHistory(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val fromLat: Double,
    val fromLon: Double,
    val fromLabel: String,
    val toLat: Double,
    val toLon: Double,
    val toLabel: String,
    val mode: Mode,
    val radiusMeters: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val arrivalAlarmRang: Boolean,
    val arrivalAlarmAt: Long?,
    val outcome: TripOutcome?
)

@Entity(
    tableName = "route_alarm_event",
    foreignKeys = [
        ForeignKey(
            entity = TripHistory::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class RouteAlarmEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val at: Long,
    val lat: Double,
    val lon: Double,
    val offRouteMeters: Double,
    val userAction: RouteAlarmAction
)

@Entity(tableName = "saved_place")
data class SavedPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val createdAt: Long
)
