package com.joyel.tripguard.share

import com.joyel.tripguard.trip.Mode
import kotlinx.serialization.Serializable

enum class PickTarget { FROM, TO }

private const val PENDING_PICK_EXPIRY_MS = 30 * 60 * 1000L

/** Trip Setup's unsaved fields, stashed so nothing is lost while the user is away in Google
 * Maps choosing a place. */
@Serializable
data class TripSetupDraft(
    val fromLat: Double? = null,
    val fromLon: Double? = null,
    val fromLabel: String = "Current location",
    val toLat: Double? = null,
    val toLon: Double? = null,
    val toLabel: String = "",
    val radiusMeters: Int = 3000,
    val mode: Mode = Mode.CAB,
    val finalAlarmEnabled: Boolean = false,
    val finalAlarmMeters: Int = com.joyel.tripguard.trip.DEFAULT_FINAL_ALARM_RADIUS_M
)

@Serializable
data class PendingPick(
    val target: PickTarget,
    val createdAt: Long,
    val draft: TripSetupDraft? = null
)

fun PendingPick.isExpired(now: Long): Boolean = now - createdAt > PENDING_PICK_EXPIRY_MS
