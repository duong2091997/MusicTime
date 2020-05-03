package com.musictime.android.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Track::class], version = 1, exportSchema = false)
abstract class TrackDatabase : RoomDatabase() {

    abstract val trackDatabaseDAO: TrackDatabaseDAO
    companion object {
        @Volatile
        private var INSTANCE: TrackDatabase? = null
        fun getInstance(context: Context): TrackDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        TrackDatabase::class.java,
                        "track_database"
                    ).fallbackToDestructiveMigration().build()
                }
                return instance
            }
        }
    }
}