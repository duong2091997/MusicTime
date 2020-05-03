package com.musictime.android.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TrackDatabaseDAO {
    @Insert
    fun insert(track: Track)

    @Update
    fun update(track: Track)

    @Query("SELECT * FROM track_table WHERE trackId = :key")
    fun get(key: Long): Track?

    @Query("DELETE FROM track_table WHERE trackId = :key")
    fun delete(key: Long)

    @Query("DELETE FROM track_table")
    fun clear()

    @Query("SELECT * FROM track_table ORDER BY trackId DESC")
    fun getAllTracks(): LiveData<List<Track>>
}