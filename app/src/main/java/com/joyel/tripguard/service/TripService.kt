package com.joyel.tripguard.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.joyel.tripguard.CHANNEL_SERVICE
import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.geo.haversineMeters
import com.joyel.tripguard.history.HistoryRepository
import com.joyel.tripguard.history.MemoryMirror
import com.joyel.tripguard.history.RouteAlarmAction
import com.joyel.tripguard.history.TripHistoryLogic
import com.joyel.tripguard.history.TripOutcome
import com.joyel.tripguard.location.IntervalSpec
import com.joyel.tripguard.location.LocationClient
import com.joyel.tripguard.location.intervalFor
import com.joyel.tripguard.routing.OsrmRoutingProvider
import com.joyel.tripguard.trip.Fix
import com.joyel.tripguard.trip.Trip
import com.joyel.tripguard.trip.TripEngine
import com.joyel.tripguard.trip.TripEvent
import com.joyel.tripguard.trip.TripRepository
import com.joyel.tripguard.trip.TripState
import com.joyel.tripguard.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val NOTIFICATION_ID = 1001
private const val GEOFENCE_ID = "arrival"
const val ACTION_START = "com.joyel.tripguard.action.START"
const val ACTION_STOP = "com.joyel.tripguard.action.STOP"
const val ACTION_GEOFENCE_ARRIVAL = "com.joyel.tripguard.action.GEOFENCE_ARRIVAL"
const val ACTION_DISMISS_ARRIVAL = "com.joyel.tripguard.action.DISMISS_ARRIVAL"
const val ACTION_DISMISS_FINAL_ARRIVAL = "com.joyel.tripguard.action.DISMISS_FINAL_ARRIVAL"
const val ACTION_REROUTE = "com.joyel.tripguard.action.REROUTE"
const val ACTION_IGNORE_ROUTE = "com.joyel.tripguard.action.IGNORE_ROUTE"

class TripService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private lateinit var repository: TripRepository
    private lateinit var historyRepo: HistoryRepository
    private lateinit var locationClient: LocationClient
    private lateinit var geofencingClient: GeofencingClient
    private var engine: TripEngine? = null
    private var stalenessJob: Job? = null
    private var trackingJob: Job? = null
    private var lowBatteryAnnounced = false
    private var currentSettings = com.joyel.tripguard.trip.AppSettings()
    private var historyRow: com.joyel.tripguard.history.TripHistory? = null
    private var lastFixLatLon: LatLon? = null
    private var traveledMeters = 0.0
    private var pendingOffRouteMeters = 0.0
    private var pendingOffRouteLocation: LatLon? = null
    private val specFlow = MutableStateFlow(IntervalSpec(30_000, com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY))

    private val gpsStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (!com.joyel.tripguard.ui.permissions.isGpsEnabled(context)) {
                AlarmController.raiseShortAlert(context, "GPS is OFF", "Your alarm cannot work until GPS is back on")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = TripRepository(applicationContext)
        historyRepo = HistoryRepository(applicationContext)
        locationClient = LocationClient(applicationContext)
        geofencingClient = LocationServices.getGeofencingClient(applicationContext)
        registerReceiver(
            gpsStateReceiver,
            android.content.IntentFilter(android.location.LocationManager.PROVIDERS_CHANGED_ACTION)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AlarmController.dismiss(applicationContext)
        when (intent?.action) {
            ACTION_STOP -> {
                AlarmController.dismiss(applicationContext)
                scope.launch {
                    val trip = engine?.trip ?: repository.getActiveTrip()
                    if (trip?.state == TripState.ROUTE_ALARMING) logRouteAlarmEvent(RouteAlarmAction.STOP)
                    engine?.stop()
                    finishHistory(TripOutcome.STOPPED_EARLY)
                    repository.saveActiveTrip(null)
                    stopTracking()
                    removeGeofence()
                    MemoryMirror.requestWrite(applicationContext)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_GEOFENCE_ARRIVAL -> {
                onArrivalEvents(engine?.triggerArrivalFromGeofence().orEmpty())
                return START_STICKY
            }
            ACTION_DISMISS_ARRIVAL -> {
                AlarmController.dismiss(applicationContext)
                scope.launch {
                    val tripEngine = engine
                    if (tripEngine != null) {
                        tripEngine.dismissArrival()
                        repository.saveActiveTrip(tripEngine.trip)
                        if (tripEngine.trip.state == TripState.FINISHED) {
                            finishHistory(TripOutcome.ARRIVED)
                            repository.saveActiveTrip(null)
                            stopTracking()
                            removeGeofence()
                            MemoryMirror.requestWrite(applicationContext)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    } else {
                        val trip = repository.getActiveTrip()
                        if (trip != null) {
                            finishHistory(TripOutcome.ARRIVED)
                        }
                        repository.saveActiveTrip(null)
                        stopTracking()
                        removeGeofence()
                        MemoryMirror.requestWrite(applicationContext)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
                return START_STICKY
            }
            ACTION_DISMISS_FINAL_ARRIVAL -> {
                AlarmController.dismiss(applicationContext)
                scope.launch {
                    val tripEngine = engine
                    if (tripEngine != null) {
                        tripEngine.dismissFinalArrival()
                        repository.saveActiveTrip(tripEngine.trip)
                        if (tripEngine.trip.state == TripState.FINISHED) {
                            finishHistory(TripOutcome.ARRIVED)
                            repository.saveActiveTrip(null)
                            stopTracking()
                            removeGeofence()
                            MemoryMirror.requestWrite(applicationContext)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    } else {
                        repository.saveActiveTrip(null)
                        stopTracking()
                        removeGeofence()
                        MemoryMirror.requestWrite(applicationContext)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
                return START_STICKY
            }
            ACTION_IGNORE_ROUTE -> {
                AlarmController.dismiss(applicationContext)
                scope.launch {
                    engine?.ignoreRouteFor10Min(SystemClock.elapsedRealtime())
                    logRouteAlarmEvent(RouteAlarmAction.IGNORE)
                    engine?.let { repository.saveActiveTrip(it.trip) }
                }
                return START_STICKY
            }
            ACTION_REROUTE -> {
                AlarmController.dismiss(applicationContext)
                reRouteFromHere()
                return START_STICKY
            }
            else -> startForeground(NOTIFICATION_ID, buildNotification("Starting…"))
        }
        beginTracking()
        return START_STICKY
    }

    private fun afterDismiss() {
        val tripEngine = engine ?: return
        scope.launch { repository.saveActiveTrip(tripEngine.trip) }
        if (tripEngine.trip.state == TripState.FINISHED) {
            finishHistory(TripOutcome.ARRIVED)
            stopTracking()
            removeGeofence()
            MemoryMirror.requestWrite(applicationContext)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun finishHistory(outcome: TripOutcome) {
        val row = historyRow ?: return
        val updated = TripHistoryLogic.finish(row, System.currentTimeMillis(), outcome)
        historyRow = updated
        scope.launch {
            historyRepo.updateTrip(updated)
            MemoryMirror.requestWrite(applicationContext)
        }
    }

    private fun logRouteAlarmEvent(action: RouteAlarmAction) {
        val tripId = engine?.trip?.id ?: return
        val loc = pendingOffRouteLocation ?: return
        val event = TripHistoryLogic.routeAlarmEvent(tripId, System.currentTimeMillis(), loc, pendingOffRouteMeters, action)
        scope.launch {
            historyRepo.insertRouteAlarmEvent(event)
            MemoryMirror.requestWrite(applicationContext)
        }
    }

    private fun onArrivalEvents(events: List<TripEvent>) {
        if (events.isEmpty()) return
        events.forEach { handleEvent(it) }
        engine?.let { scope.launch { repository.saveActiveTrip(it.trip) } }
    }

    private fun beginTracking() {
        trackingJob = scope.launch {
            val trip = repository.getActiveTrip()
            if (trip == null || trip.state == TripState.FINISHED) {
                stopSelf()
                return@launch
            }
            runTrip(trip)
        }
    }

    private suspend fun runTrip(trip: Trip) {
        val settings = com.joyel.tripguard.trip.SettingsRepository(applicationContext).get()
        currentSettings = settings
        val tripEngine = TripEngine(
            trip,
            offRouteThresholdM = settings.routeThresholdM.toDouble(),
            offRouteConsecutiveRequired = settings.routeConsecutiveCount
        )
        engine = tripEngine
        addArrivalGeofence(trip)

        val fromState = com.joyel.tripguard.location.LocationRepositoryHolder.get(applicationContext).state.value
        val fromLatLon = (fromState as? com.joyel.tripguard.location.LocationState.Ready)
            ?.let { LatLon(it.lat, it.lon) } ?: trip.destination
        historyRow = TripHistoryLogic.start(trip, fromLatLon, "Starting point", System.currentTimeMillis())
        historyRow?.let { row ->
            scope.launch {
                historyRepo.insertTrip(row)
                MemoryMirror.requestWrite(applicationContext)
            }
        }

        stalenessJob = scope.launch {
            while (true) {
                delay(30_000)
                val events = tripEngine.checkStaleness(SystemClock.elapsedRealtime())
                events.forEach { handleEvent(it) }
                checkLowBattery()
            }
        }

        locationClient.locationUpdates(specFlow).collectLatest { location ->
            val nowElapsed = SystemClock.elapsedRealtime()
            val fix = Fix(
                lat = location.latitude,
                lon = location.longitude,
                accuracyM = location.accuracy,
                timestampMs = location.time,
                elapsedRealtimeMs = nowElapsed
            )
            val events = tripEngine.process(fix, nowElapsed)
            val distance = haversineMeters(fix.latLon, tripEngine.trip.destination)
            specFlow.value = intervalFor(tripEngine.trip.mode, distance)

            lastFixLatLon?.let { traveledMeters += haversineMeters(it, fix.latLon) }
            lastFixLatLon = fix.latLon
            historyRow?.let { row ->
                val updated = TripHistoryLogic.onProgress(row, traveledMeters, System.currentTimeMillis())
                historyRow = updated
                scope.launch { historyRepo.updateTrip(updated) }
            }

            TripServiceHolder.state.value = TripServiceHolder.state.value.copy(
                trip = tripEngine.trip,
                distanceMeters = distance,
                accuracyM = fix.accuracyM,
                intervalMs = specFlow.value.intervalMs,
                statusText = statusFor(tripEngine.trip, events)
            )

            repository.saveActiveTrip(tripEngine.trip)
            events.forEach { handleEvent(it) }

            if (tripEngine.trip.state == TripState.FINISHED) {
                stopTracking()
                removeGeofence()
                MemoryMirror.requestWrite(applicationContext)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                updateNotification(distance)
            }
        }
    }

    private fun handleEvent(event: TripEvent) {
        when (event) {
            is TripEvent.ArrivalReached -> {
                val km = event.distanceM / 1000.0
                scope.launch { historyRow?.let { historyRepo.updateTrip(TripHistoryLogic.onArrivalAlarm(it, System.currentTimeMillis()).also { row -> historyRow = row }) } }
                AlarmController.raiseLoudAlarm(
                    this, AlarmKind.ARRIVAL, "WAKE UP",
                    "%.1f km to destination".format(km),
                    maxVolume = currentSettings.maxVolumeOnAlarm,
                    vibrate = currentSettings.vibrateOnAlarm
                )
            }
            is TripEvent.FinalArrivalReached -> {
                scope.launch { historyRow?.let { historyRepo.updateTrip(TripHistoryLogic.onArrivalAlarm(it, System.currentTimeMillis()).also { row -> historyRow = row }) } }
                AlarmController.raiseLoudAlarm(
                    this, AlarmKind.FINAL_ARRIVAL, "WAKE UP",
                    "%.0f m to destination".format(event.distanceM),
                    maxVolume = currentSettings.maxVolumeOnAlarm,
                    vibrate = currentSettings.vibrateOnAlarm
                )
            }
            is TripEvent.OffRouteDetected -> {
                pendingOffRouteMeters = event.offRouteM
                pendingOffRouteLocation = lastFixLatLon
                AlarmController.raiseLoudAlarm(
                    this, AlarmKind.OFF_ROUTE, "WRONG ROUTE",
                    "%.0f m off your route".format(event.offRouteM),
                    maxVolume = currentSettings.maxVolumeOnAlarm,
                    vibrate = currentSettings.vibrateOnAlarm,
                    onTimeout = {
                        engine?.dismissRouteAlarm(SystemClock.elapsedRealtime())
                        logRouteAlarmEvent(RouteAlarmAction.TIMED_OUT)
                        engine?.let { scope.launch { repository.saveActiveTrip(it.trip) } }
                    }
                )
            }
            is TripEvent.GpsWeak ->
                AlarmController.raiseShortAlert(this, "GPS weak", "No reliable fix for over 90 seconds")
            is TripEvent.GpsRecovered -> Unit
            is TripEvent.NoFixTooLong ->
                AlarmController.raiseShortAlert(this, "No GPS fix", "No fix for over 5 minutes")
        }
    }

    private fun checkLowBattery() {
        if (lowBatteryAnnounced) return
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (pct in 1..10) {
            lowBatteryAnnounced = true
            AlarmController.raiseShortAlert(this, "Battery low", "$pct% remaining")
        }
    }

    private fun reRouteFromHere() {
        val tripEngine = engine ?: return
        scope.launch {
            val current = locationClient.lastKnownLocation()
            if (current == null) {
                AlarmController.raiseShortAlert(this@TripService, "Re-route failed", "No current location, kept previous route")
                afterDismiss()
                return@launch
            }
            val from = com.joyel.tripguard.geo.LatLon(current.latitude, current.longitude)
            val newRoute = OsrmRoutingProvider().route(from, tripEngine.trip.destination)
            if (newRoute == null) {
                AlarmController.raiseShortAlert(this@TripService, "Re-route failed", "Route unavailable, kept previous route")
                engine?.dismissRouteAlarm(SystemClock.elapsedRealtime())
            } else {
                tripEngine.reRoute(newRoute)
                logRouteAlarmEvent(RouteAlarmAction.REROUTE)
            }
            afterDismiss()
        }
    }

    @SuppressLint("MissingPermission")
    private fun addArrivalGeofence(trip: Trip) {
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(trip.destLat, trip.destLon, trip.radiusMeters.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, Intent(this, GeofenceReceiver::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        geofencingClient.addGeofences(request, pendingIntent)
    }

    private fun removeGeofence() {
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, Intent(this, GeofenceReceiver::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        geofencingClient.removeGeofences(pendingIntent)
    }

    private fun statusFor(trip: Trip, events: List<TripEvent>): String = when {
        events.any { it is TripEvent.ArrivalReached || it is TripEvent.FinalArrivalReached } -> "Arrived"
        events.any { it is TripEvent.OffRouteDetected } -> "Off route"
        events.any { it is TripEvent.GpsWeak } -> "GPS weak"
        trip.state == TripState.FINISHED -> "Finished"
        else -> "Tracking"
    }

    private fun stopTracking() {
        stalenessJob?.cancel()
        trackingJob?.cancel()
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setContentTitle("Knock Me")
            .setContentText(text)
            .setSmallIcon(com.joyel.tripguard.R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun updateNotification(distanceMeters: Double) {
        val km = distanceMeters / 1000.0
        val text = "%.1f km to destination".format(km)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        stopTracking()
        runCatching { unregisterReceiver(gpsStateReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
