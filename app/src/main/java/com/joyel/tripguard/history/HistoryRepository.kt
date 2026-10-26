package com.joyel.tripguard.history

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Thin wrapper over the Room DAOs; all calls are safe to call from any coroutine dispatcher. */
class HistoryRepository(context: Context) {
    private val db = HistoryDatabase.get(context)
    private val tripDao = db.tripHistoryDao()
    private val eventDao = db.routeAlarmEventDao()
    private val placeDao = db.savedPlaceDao()

    suspend fun insertTrip(trip: TripHistory) = tripDao.insert(trip)
    suspend fun updateTrip(trip: TripHistory) = tripDao.update(trip)
    suspend fun deleteTrip(id: String) = tripDao.delete(id)
    suspend fun clearAllTrips() = tripDao.clearAll()
    fun observeTrips(): Flow<List<TripHistory>> = tripDao.observeAll()
    /** Last [limit] trips, deduped by (from, to) pair so repeats don't crowd out other destinations. */
    fun observeRecentTrips(limit: Int): Flow<List<TripHistory>> =
        tripDao.observeRecent(limit * 5).map { rows ->
            rows.distinctBy { it.fromLabel to it.toLabel }.take(limit)
        }
    suspend fun getTrip(id: String): TripHistory? = tripDao.getById(id)
    suspend fun getUnfinishedTrips(): List<TripHistory> = tripDao.getUnfinished()

    suspend fun insertRouteAlarmEvent(event: RouteAlarmEvent) = eventDao.insert(event)
    suspend fun getRouteAlarmEvents(tripId: String): List<RouteAlarmEvent> = eventDao.getForTrip(tripId)
    suspend fun countRouteAlarmEvents(tripId: String): Int = eventDao.countForTrip(tripId)

    suspend fun insertSavedPlace(place: SavedPlace): Long = placeDao.insert(place)
    suspend fun updateSavedPlace(place: SavedPlace) = placeDao.update(place)
    suspend fun deleteSavedPlace(place: SavedPlace) = placeDao.delete(place)
    /** Home/Office first (if present), then the rest in creation order. */
    fun observeSavedPlaces(): Flow<List<SavedPlace>> = placeDao.observeAll().map { places ->
        val priority = { name: String -> when (name.trim().lowercase()) {
            "home" -> 0
            "office" -> 1
            else -> 2
        } }
        places.sortedBy { priority(it.name) }
    }
}
