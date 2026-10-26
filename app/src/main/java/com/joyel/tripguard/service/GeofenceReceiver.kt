package com.joyel.tripguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/** Backup arrival trigger (PRD 7.3): whichever of the fix stream or this geofence fires first wins. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            context.startService(Intent(context, TripService::class.java).setAction(ACTION_GEOFENCE_ARRIVAL))
        }
    }
}
