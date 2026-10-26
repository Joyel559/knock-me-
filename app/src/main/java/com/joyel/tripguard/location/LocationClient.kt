package com.joyel.tripguard.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest

/** Wraps FusedLocationProviderClient; re-issues the request whenever [IntervalSpec] changes. */
class LocationClient(context: Context) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    private fun rawUpdates(spec: IntervalSpec): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(spec.intervalMs)
            .setPriority(spec.priority)
            .setMinUpdateIntervalMillis(spec.intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }

    /** Collects a single location stream, restarting the fused request whenever [specs] changes. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun locationUpdates(specs: Flow<IntervalSpec>): Flow<Location> =
        specs.flatMapLatest { spec -> rawUpdates(spec) }

    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): Location? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            fused.lastLocation
                .addOnSuccessListener { cont.resume(it) {} }
                .addOnFailureListener { cont.resume(null) {} }
        }
}
