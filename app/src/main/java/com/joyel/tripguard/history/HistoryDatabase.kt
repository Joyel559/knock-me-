package com.joyel.tripguard.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TripHistory::class, RouteAlarmEvent::class, SavedPlace::class],
    version = 2,
    exportSchema = false
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun tripHistoryDao(): TripHistoryDao
    abstract fun routeAlarmEventDao(): RouteAlarmEventDao
    abstract fun savedPlaceDao(): SavedPlaceDao

    companion object {
        @Volatile private var instance: HistoryDatabase? = null

        fun get(context: Context): HistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HistoryDatabase::class.java,
                "knockme_history.db"
            ).fallbackToDestructiveMigration(true).build().also { instance = it }
        }
    }
}
