package com.joyel.tripguard.history

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripHistoryDao {
    @Insert
    suspend fun insert(trip: TripHistory)

    @Update
    suspend fun update(trip: TripHistory)

    @Query("DELETE FROM trip_history WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM trip_history")
    suspend fun clearAll()

    @Query("SELECT * FROM trip_history ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<TripHistory>>

    @Query("SELECT * FROM trip_history ORDER BY startedAt DESC")
    suspend fun getAll(): List<TripHistory>

    @Query("SELECT * FROM trip_history ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TripHistory>>

    @Query("SELECT * FROM trip_history WHERE id = :id")
    suspend fun getById(id: String): TripHistory?

    @Query("SELECT * FROM trip_history WHERE endedAt IS NULL")
    suspend fun getUnfinished(): List<TripHistory>
}

@Dao
interface RouteAlarmEventDao {
    @Insert
    suspend fun insert(event: RouteAlarmEvent)

    @Query("SELECT * FROM route_alarm_event WHERE tripId = :tripId ORDER BY at ASC")
    suspend fun getForTrip(tripId: String): List<RouteAlarmEvent>

    @Query("SELECT * FROM route_alarm_event WHERE tripId = :tripId ORDER BY at ASC")
    suspend fun getForTripSuspend(tripId: String): List<RouteAlarmEvent>

    @Query("SELECT COUNT(*) FROM route_alarm_event WHERE tripId = :tripId")
    suspend fun countForTrip(tripId: String): Int
}

@Dao
interface SavedPlaceDao {
    @Insert
    suspend fun insert(place: SavedPlace): Long

    @Update
    suspend fun update(place: SavedPlace)

    @Delete
    suspend fun delete(place: SavedPlace)

    @Query("SELECT * FROM saved_place ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SavedPlace>>

    @Query("SELECT * FROM saved_place ORDER BY createdAt ASC")
    suspend fun getAll(): List<SavedPlace>
}
