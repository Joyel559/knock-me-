package com.joyel.tripguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.joyel.tripguard.trip.TripRepository
import com.joyel.tripguard.trip.TripState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Resumes the active trip's foreground service after a reboot (PRD 6.3, 12). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val trip = TripRepository(context.applicationContext).getActiveTrip()
                if (trip != null && trip.state != TripState.FINISHED) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, TripService::class.java).setAction(ACTION_START)
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
