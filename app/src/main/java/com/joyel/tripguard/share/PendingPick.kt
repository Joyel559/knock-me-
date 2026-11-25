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

/** Which field an incoming share should fill: the pick's target if one is pending (and not
 * expired), else To — "a share with no pending pick fills To". */
fun resolveShareTarget(pick: PendingPick?): PickTarget = pick?.target ?: PickTarget.TO

/** True only when [resolveShareTarget] landed on To because nothing was pending (not because the
 * user explicitly picked To) — drives the "Use as start instead" button. */
fun shareTargetWasDefault(pick: PendingPick?): Boolean = pick == null

/** Offer the clipboard-paste bottom sheet only while a pick is pending, for text that looks like
 * a place and wasn't already offered (and dismissed/used) last time. */
fun shouldOfferClipboard(hasPendingPick: Boolean, clipboardText: String?, lastOfferedText: String?): Boolean {
    if (!hasPendingPick) return false
    if (clipboardText.isNullOrBlank()) return false
    if (clipboardText == lastOfferedText) return false
    return looksLikePlaceText(clipboardText)
}
