package com.joyel.tripguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.joyel.tripguard.trip.ReliabilityRepository
import com.joyel.tripguard.trip.TripRepository
import com.joyel.tripguard.trip.TripState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Resumes the active trip's foreground service after a reboot — Max reliability only (PRD 6.3,
 * 12, v1.5 part 4). In Lean mode this does nothing: a boot-time background start of a
 * location-type foreground service can be refused by the OS without background location, and
 * Lean mode never asks for that; the trip resumes instead when the user next opens the app
 * (see HomeScreen's resume flow).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val maxReliability = ReliabilityRepository(appContext).get().maxReliabilityEnabled
                if (!maxReliability) return@launch
                val trip = TripRepository(appContext).getActiveTrip()
                if (trip != null && trip.state != TripState.FINISHED) {
                    runCatching {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, TripService::class.java).setAction(ACTION_START)
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
