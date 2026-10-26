package com.joyel.tripguard.history

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream

@Serializable
data class MemoryExport(
    val appName: String = "Knock Me",
    val updatedAt: Long,
    val savedPlaces: List<SavedPlaceExport>,
    val trips: List<TripExport>
)

@Serializable
data class SavedPlaceExport(
    val id: Long,
    val name: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val createdAt: Long
)

@Serializable
data class TripExport(
    val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val fromLat: Double,
    val fromLon: Double,
    val fromLabel: String,
    val toLat: Double,
    val toLon: Double,
    val toLabel: String,
    val mode: String,
    val radiusMeters: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val arrivalAlarmRang: Boolean,
    val arrivalAlarmAt: Long?,
    val outcome: String?,
    val routeAlarmEvents: List<RouteAlarmEventExport>
)

@Serializable
data class RouteAlarmEventExport(
    val id: Long,
    val at: Long,
    val place: String,
    val lat: Double,
    val lon: Double,
    val offRouteMeters: Double,
    val userAction: String
)

object MemoryMirror {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var lastWriteAttempt = 0L

    fun requestWrite(context: Context) {
        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                if (now - lastWriteAttempt < 5000L) {
                    delay(5000L - (now - lastWriteAttempt))
                }
                lastWriteAttempt = System.currentTimeMillis()
                runCatching { writeMemoryFile(context.applicationContext) }
            }
        }
    }

    suspend fun writeNow(context: Context) {
        mutex.withLock {
            runCatching { writeMemoryFile(context.applicationContext) }
        }
    }

    private suspend fun writeMemoryFile(context: Context) {
        val db = HistoryDatabase.get(context)
        val trips = db.tripHistoryDao().getAll()
        val places = db.savedPlaceDao().getAll()
        val eventDao = db.routeAlarmEventDao()

        val tripExports = trips.map { trip ->
            val events = eventDao.getForTripSuspend(trip.id)
            TripExport(
                id = trip.id,
                startedAt = trip.startedAt,
                endedAt = trip.endedAt,
                fromLat = trip.fromLat,
                fromLon = trip.fromLon,
                fromLabel = trip.fromLabel,
                toLat = trip.toLat,
                toLon = trip.toLon,
                toLabel = trip.toLabel,
                mode = trip.mode.name,
                radiusMeters = trip.radiusMeters,
                distanceMeters = trip.distanceMeters,
                durationSeconds = trip.durationSeconds,
                arrivalAlarmRang = trip.arrivalAlarmRang,
                arrivalAlarmAt = trip.arrivalAlarmAt,
                outcome = trip.outcome?.name,
                routeAlarmEvents = events.map {
                    RouteAlarmEventExport(
                        id = it.id,
                        at = it.at,
                        place = "%.4f,%.4f".format(it.lat, it.lon),
                        lat = it.lat,
                        lon = it.lon,
                        offRouteMeters = it.offRouteMeters,
                        userAction = it.userAction.name
                    )
                }
            )
        }

        val placeExports = places.map {
            SavedPlaceExport(
                id = it.id,
                name = it.name,
                label = it.label,
                lat = it.lat,
                lon = it.lon,
                createdAt = it.createdAt
            )
        }

        val memory = MemoryExport(
            updatedAt = System.currentTimeMillis(),
            savedPlaces = placeExports,
            trips = tripExports
        )

        val jsonString = Json { prettyPrint = true }.encodeToString(memory)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/Knock Me Memory"
            val displayName = "knock_me_memory.json"
            val collection = MediaStore.Files.getContentUri("external")

            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf("$relativePath/", displayName)

            var existingUri: Uri? = null
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    existingUri = ContentUris.withAppendedId(collection, id)
                }
            }

            val uri = if (existingUri != null) {
                existingUri!!
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
                resolver.insert(collection, values) ?: return
            }

            resolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(jsonString.toByteArray(Charsets.UTF_8))
            }
        } else {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val targetDir = File(documentsDir, "Knock Me Memory")
            if (!targetDir.exists()) targetDir.mkdirs()
            val file = File(targetDir, "knock_me_memory.json")
            file.writeText(jsonString, Charsets.UTF_8)
        }
    }

    suspend fun importFromFile(context: Context, inputStream: InputStream): Boolean {
        return runCatching {
            val text = inputStream.bufferedReader().use { it.readText() }
            val memory = Json.decodeFromString<MemoryExport>(text)
            val db = HistoryDatabase.get(context)
            val tripDao = db.tripHistoryDao()
            val placeDao = db.savedPlaceDao()
            val eventDao = db.routeAlarmEventDao()

            for (p in memory.savedPlaces) {
                val place = SavedPlace(
                    id = if (p.id > 0) p.id else 0,
                    name = p.name,
                    label = p.label,
                    lat = p.lat,
                    lon = p.lon,
                    createdAt = p.createdAt
                )
                runCatching { placeDao.insert(place) }
            }

            for (t in memory.trips) {
                val trip = TripHistory(
                    id = t.id,
                    startedAt = t.startedAt,
                    endedAt = t.endedAt,
                    fromLat = t.fromLat,
                    fromLon = t.fromLon,
                    fromLabel = t.fromLabel,
                    toLat = t.toLat,
                    toLon = t.toLon,
                    toLabel = t.toLabel,
                    mode = com.joyel.tripguard.trip.Mode.valueOf(t.mode),
                    radiusMeters = t.radiusMeters,
                    distanceMeters = t.distanceMeters,
                    durationSeconds = t.durationSeconds,
                    arrivalAlarmRang = t.arrivalAlarmRang,
                    arrivalAlarmAt = t.arrivalAlarmAt,
                    outcome = t.outcome?.let { com.joyel.tripguard.history.TripOutcome.valueOf(it) }
                )
                runCatching { tripDao.insert(trip) }

                for (e in t.routeAlarmEvents) {
                    val event = RouteAlarmEvent(
                        id = if (e.id > 0) e.id else 0,
                        tripId = t.id,
                        at = e.at,
                        lat = e.lat,
                        lon = e.lon,
                        offRouteMeters = e.offRouteMeters,
                        userAction = com.joyel.tripguard.history.RouteAlarmAction.valueOf(e.userAction)
                    )
                    runCatching { eventDao.insert(event) }
                }
            }
            true
        }.getOrDefault(false)
    }
}
