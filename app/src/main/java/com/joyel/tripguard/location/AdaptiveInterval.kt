package com.joyel.tripguard.location

import com.google.android.gms.location.Priority
import com.joyel.tripguard.trip.Mode

data class IntervalSpec(val intervalMs: Long, val priority: Int)

/** Battery strategy table from the PRD (section 8). */
fun intervalFor(mode: Mode, distanceToDestinationMeters: Double): IntervalSpec = when {
    distanceToDestinationMeters > 30_000 ->
        if (mode == Mode.CAB) IntervalSpec(15_000, Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        else IntervalSpec(60_000, Priority.PRIORITY_BALANCED_POWER_ACCURACY)

    distanceToDestinationMeters > 10_000 ->
        if (mode == Mode.CAB) IntervalSpec(15_000, Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        else IntervalSpec(30_000, Priority.PRIORITY_BALANCED_POWER_ACCURACY)

    distanceToDestinationMeters > 5_000 ->
        if (mode == Mode.CAB) IntervalSpec(10_000, Priority.PRIORITY_HIGH_ACCURACY)
        else IntervalSpec(15_000, Priority.PRIORITY_HIGH_ACCURACY)

    else -> IntervalSpec(5_000, Priority.PRIORITY_HIGH_ACCURACY)
}
