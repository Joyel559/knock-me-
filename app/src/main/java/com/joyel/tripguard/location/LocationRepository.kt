package com.joyel.tripguard.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.joyel.tripguard.ui.permissions.isGpsEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed class LocationState {
    object NoPermission : LocationState()
    object GpsOff : LocationState()
    object Searching : LocationState()
    data class Ready(
        val lat: Double,
        val lon: Double,
        val accuracyMeters: Float,
        val timestampMs: Long
    ) : LocationState()
}

private const val FRESH_FIX_MAX_AGE_MS = 2 * 60 * 1000L
private const val CURRENT_LOCATION_TIMEOUT_MS = 15_000L
private const val GOOD_ACCURACY_M = 100f

/**
 * Observable current-location fetcher. Tries last-location + a fresh high-accuracy request in
 * parallel, falls back to continuous updates until a decent fix arrives, and falls back again to
 * the platform LocationManager if Play services is unavailable. Never throws into the UI — bad
 * states surface as [LocationState] values, not exceptions.
 */
class LocationRepository(private val context: Context) {
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val playServicesOk: Boolean
        get() = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    private val _state = MutableStateFlow<LocationState>(LocationState.Searching)
    val state: StateFlow<LocationState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Starts (or resumes) searching for a fix. Safe to call repeatedly; a no-op while already running. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runLoop() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runLoop() {
        while (true) {
            if (!hasPermission()) {
                _state.value = LocationState.NoPermission
                delay(2000)
                continue
            }
            if (!isGpsEnabled(context)) {
                _state.value = LocationState.GpsOff
                delay(2000)
                continue
            }
            _state.value = LocationState.Searching
            val fix = findFix()
            if (fix != null) {
                _state.value = fix
                return
            }
            // findFix() only returns null if permission/GPS dropped mid-search; loop re-checks.
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun findFix(): LocationState.Ready? {
        if (!playServicesOk) return platformFix()

        val lastDeferred = scope.async { runCatching { awaitLast() }.getOrNull() }
        val currentDeferred = scope.async {
            withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
                runCatching { awaitCurrent() }.getOrNull()
            }
        }

        val last = lastDeferred.await()
        if (last != null && System.currentTimeMillis() - last.time < FRESH_FIX_MAX_AGE_MS) {
            currentDeferred.cancel()
            return last.toReady()
        }
        val current = currentDeferred.await()
        if (current != null) return current.toReady()

        return requestUpdatesUntilGoodFix()
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitLast(): Location? = suspendCancellableCoroutine { cont ->
        fused.lastLocation
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrent(): Location? = suspendCancellableCoroutine { cont ->
        val cancelSource = CancellationTokenSource()
        cont.invokeOnCancellation { cancelSource.cancel() }
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        fused.getCurrentLocation(request, cancelSource.token)
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestUpdatesUntilGoodFix(): LocationState.Ready? {
        val request = LocationRequest.Builder(2000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setWaitForAccurateLocation(false)
            .build()
        return withTimeoutOrNull(120_000) {
            callbackFlow {
                val callback = object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                        result.lastLocation?.let { trySend(it) }
                    }
                }
                fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
                awaitClose { fused.removeLocationUpdates(callback) }
            }.first { it.accuracy <= GOOD_ACCURACY_M }.toReady()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun platformFix(): LocationState.Ready? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val lastGood = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < FRESH_FIX_MAX_AGE_MS }
            .minByOrNull { it.accuracy }
        if (lastGood != null) return lastGood.toReady()

        return withTimeoutOrNull(60_000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) {
                            lm.removeUpdates(this)
                            cont.resume(location.toReady())
                        }
                    }
                }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
                runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0f, listener, Looper.getMainLooper()) }
                runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0f, listener, Looper.getMainLooper()) }
            }
        }
    }

    private fun Location.toReady() = LocationState.Ready(latitude, longitude, accuracy, time)
}

/** App-scoped singleton so Home and Trip Setup observe the same in-flight search. */
object LocationRepositoryHolder {
    private var instance: LocationRepository? = null

    fun get(context: Context): LocationRepository {
        return instance ?: LocationRepository(context.applicationContext).also { instance = it }
    }
}

/** Builds the system "turn on GPS" resolution task for [android.app.Activity.startIntentSenderForResult]. */
fun locationSettingsTask(context: Context) = com.google.android.gms.location.LocationServices
    .getSettingsClient(context)
    .checkLocationSettings(
        com.google.android.gms.location.LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(2000).setPriority(Priority.PRIORITY_HIGH_ACCURACY).build())
            .build()
    )
