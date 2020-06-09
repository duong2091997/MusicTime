package com.musictime.android.media.library

import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import androidx.annotation.IntDef

interface MusicSource : Iterable<MediaMetadataCompat> {

    suspend fun load()

    fun whenReady(performAction: (Boolean) -> Unit) : Boolean

    fun search(query: String, extras: Bundle): List<MediaMetadataCompat>
}

@IntDef(
    STATE_CREATED,
    STATE_INITIALIZING,
    STATE_INITIALIZED,
    STATE_ERROR
)
@Retention(AnnotationRetention.SOURCE)
annotation class State

const val STATE_CREATED = 1

const val STATE_INITIALIZING = 2

const val STATE_INITIALIZED = 3

const val STATE_ERROR = 4

abstract class AbstractMusicSource : MusicSource {
    
    private val onReadyListeners = mutableListOf<(Boolean) -> Unit>()

    @State
    var state: Int = STATE_CREATED
        set(value) {
            if (value == STATE_INITIALIZED || value == STATE_ERROR) {
                synchronized(onReadyListeners) {
                    field = value
                    onReadyListeners.forEach {listener ->
                        listener(state == STATE_INITIALIZED)                        
                    }
                }
            } else {
                field = value
            }
        }

    override fun whenReady(performAction: (Boolean) -> Unit): Boolean =
        when (state) {
            STATE_CREATED, STATE_INITIALIZING -> {
                onReadyListeners += performAction
                false
            }
            else -> {
                performAction(state != STATE_ERROR)
                true
            }
        }

    override fun search(query: String, extras: Bundle): List<MediaMetadataCompat> {
        val focusSearchResult = when (extras[MediaStore.EXTRA_MEDIA_FOCUS]) {
            MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
                val genre = extras[MediaStore.EXTRA_MEDIA_GENRE]
                Log.d(TAG, "Focused genre search: '$genre'")
                filter { song -> 
                    song.genre == genre
                }
            }
            MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> {
                val artist = extras[MediaStore.EXTRA_MEDIA_ARTIST]
                Log.d(TAG, "Focused artist search: '$artist")
                filter { song ->
                    song.artist == artist || song.albumArtist == artist
                }
            }
            
        }
    }
}

private const val TAG = "MusicSource"