package com.musictime.android.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.lang.Exception

@RunWith(AndroidJUnit4::class)
class TrackDatabaseTest {

    private lateinit var trackDatabaseDAO: TrackDatabaseDAO
    private lateinit var db: TrackDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, TrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trackDatabaseDAO = db.trackDatabaseDAO
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetTrack() {
        val track = Track()
        track.title = "test track"
        val track2 = Track()

        //TODO: Make this testcase works
        trackDatabaseDAO.insert(track)
        trackDatabaseDAO.insert(track2)
        val actualTrack = trackDatabaseDAO.get(1)
//        val t1 = actualTrack.value?.get(0)
//        val t2 = actualTrack.value?.get(1)

        assertEquals(track.title, actualTrack?.title)
    }
}