package com.musictime.android.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_table")
data class Track(
    @PrimaryKey(autoGenerate = true)
    var trackId: Long = 0L,

    @ColumnInfo(name = "file_path")
    var file_path: String = "",

    @ColumnInfo(name = "title")
    var title: String = "",

    @ColumnInfo(name = "artist")
    var artist: String = "",

    @ColumnInfo(name = "album")
    var album: String = "",

    @ColumnInfo(name = "year")
    var year: Int = -1,

    @ColumnInfo(name = "genre")
    var genre: String = "")