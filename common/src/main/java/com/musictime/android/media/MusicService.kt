package com.musictime.android.media

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import com.example.android.uamp.media.PackageValidator
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.musictime.android.R
import com.musictime.android.extensions.flag
import com.musictime.android.media.library.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


open class MusicService : MediaBrowserServiceCompat() {
    
    private lateinit var becomingNoisyReceiver: BecomingNoisyReceiver
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationBuilder: NotificationBuilder
    private lateinit var mediaSource: MusicSource
    private lateinit var packageValidator: PackageValidator
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private lateinit var mediaSession: MediaSessionCompat
    protected lateinit var mediaController: MediaControllerCompat
    protected lateinit var mediaSessionConnector: MediaSessionConnector
    
    private val browseTree: BrowseTree by lazy { 
        BrowseTree(applicationContext, mediaSource)
    }
    
    private var isForegroundService = false
    
    
    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()
    
    private val exoPlayer: ExoPlayer by lazy { 
        ExoPlayerFactory.newSimpleInstance(this).apply { 
            setAudioAttributes(audioAttributes, true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val sessionActivityPendingIntent = 
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                PendingIntent.getActivity(this, 0, sessionIntent, 0)
            }
        
        mediaSession = MediaSessionCompat(this, "MusicService")
            .apply { 
                setSessionActivity(sessionActivityPendingIntent)
                isActive = true
            }
        
        sessionToken = mediaSession.sessionToken
        
        mediaController = MediaControllerCompat(this, mediaSession).also { 
            it.registerCallback(MediaControllerCallback())
        }
        
        notificationBuilder = NotificationBuilder(this)
        notificationManager = NotificationManagerCompat.from(this)
        
        becomingNoisyReceiver = BecomingNoisyReceiver(context = this, sessionToken = mediaSession.sessionToken)
        
        // TODO: Instantiate mediaSource here!
        
        serviceScope.launch { 
            mediaSource.load()
        }
        
        mediaSessionConnector = MediaSessionConnector(mediaSession).also { connector -> 
            val dataSourceFactory = DefaultDataSourceFactory(
                this, Util.getUserAgent(this, USER_AGENT), null
            )
            
            val playbackPreparer = MtPlaybackPreparer(
                mediaSource,
                exoPlayer,
                dataSourceFactory
            )
            
            connector.setPlayer(exoPlayer)
            connector.setPlaybackPreparer(playbackPreparer)
            connector.setQueueNavigator(MtQueueNavigator(mediaSession))
        }
        
        packageValidator = PackageValidator(this, R.xml.allowed_media_browser_callers)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        
        exoPlayer.stop(true)
    }

    override fun onDestroy() {
        mediaSession.run { 
            isActive = false
            release()
        }
        
        serviceJob.cancel()
    }
    
    override fun onLoadChildren(
        parentMediaId: String,
        result: Result<List<MediaItem>>
    ) {
        val resultsSent = mediaSource.whenReady { successfullyInitialized -> 
            if (successfullyInitialized) {
                val children = browseTree[parentMediaId]?.map { item ->
                    MediaItem(item.description, item.flag)
                }
                result.sendResult(children)
            } else {
                mediaSession.sendSessionEvent(NETWORK_FAILURE, null)
                result.sendResult(null)
            }
        }

        // If the results are not ready, the service must "detach" the results before
        // the method returns. After the source is ready, the lambda above will run,
        // and the caller will be notified that the results are ready.
        //
        // See [MediaItemFragmentViewModel.subscriptionCallback] for how this is passed to the
        // UI/displayed in the [RecyclerView].
        if (!resultsSent) {
            result.detach()
        }
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaItem>>
    ) {
        val resultsSent = mediaSource.whenReady { successfullyInitialized ->
            if (successfullyInitialized) {
                val resultsList = mediaSource.search(query, extras ?: Bundle.EMPTY)
                    .map { mediaMetadata ->
                        MediaItem(mediaMetadata.description, mediaMetadata.flag)
                    }
                result.sendResult(resultsList)
            }
        }

        if (!resultsSent) {
            result.detach()
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        // TODO: verify caller
        val isKnownCaller = true
        val rootExtras = Bundle().apply { 
            putBoolean(
                MEDIA_SEARCH_SUPPORTED,
                isKnownCaller || browseTree.searchableByUnknownCaller
            )
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
        }
        
        return if (isKnownCaller) {
            BrowserRoot(UAMP_BROWSABLE_ROOT, rootExtras)
        } else {
            BrowserRoot(UAMP_EMPTY_ROOT, rootExtras)
        }
    }


    private fun removeNowPlayingNotification() {
        stopForeground(true)
    }
    
    private inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            mediaController.playbackState?.let { state -> 
                serviceScope.launch { 
                    updateNotification(state)
                }
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            state?.let { state -> 
                serviceScope.launch { 
                    updateNotification(state)
                }
            }
        }
        
        private suspend fun updateNotification(state: PlaybackStateCompat) {
            val updateState = state.state
            val notification = if (mediaController.metadata != null 
                && updateState != PlaybackStateCompat.STATE_NONE) {
                notificationBuilder.buildNotification(mediaSession.sessionToken)
            } else {
                null
            }
            
            when (updateState) {
                PlaybackStateCompat.STATE_BUFFERING, 
                PlaybackStateCompat.STATE_PLAYING -> {
                    becomingNoisyReceiver.register()
                    
                    if (notification != null) {
                        notificationManager.notify(NOW_PLAYING_NOTIFICATION, notification)
                        
                        if (!isForegroundService) {
                            ContextCompat.startForegroundService(
                                applicationContext,
                                Intent(applicationContext, this@MusicService.javaClass)
                            )
                            startForeground(NOW_PLAYING_NOTIFICATION, notification)
                            isForegroundService = true
                        }
                    }
                }
                else -> {
                    becomingNoisyReceiver.unregister()
                    
                    if (isForegroundService) {
                        stopForeground(false)
                        isForegroundService = false
                        
                        if (updateState == PlaybackStateCompat.STATE_NONE) {
                            stopSelf()
                        }
                        
                        if (notification != null) {
                            notificationManager.notify(NOW_PLAYING_NOTIFICATION, notification)
                        } else {
                            removeNowPlayingNotification()
                        }
                    }
                }
            }
        }
    }
    
}


private class BecomingNoisyReceiver(
    private val context: Context, 
    sessionToken: MediaSessionCompat.Token
) : BroadcastReceiver() {
    
    private val noisyIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private val controller = MediaControllerCompat(context, sessionToken)
    
    private var registered = false
    
    fun register() {
        if (!registered) {
            context.registerReceiver(this, noisyIntentFilter)
            registered = true
        }
    }

    fun unregister() {
        if (registered) {
            context.unregisterReceiver(this)
            registered = false
        }
    }
    
    override fun onReceive(context: Context?, intent: Intent) {
        if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            controller.transportControls.pause()
        }
    }

}

private class MtQueueNavigator(
    mediaSession: MediaSessionCompat
) : TimelineQueueNavigator(mediaSession) {
    private val window = Timeline.Window()
    override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat =
        player.currentTimeline
            .getWindow(windowIndex, window, true).tag as MediaDescriptionCompat
}

const val NETWORK_FAILURE = "com.musictime.android.media.session.NETWORK_FAILURE"

private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
private const val CONTENT_STYLE_LIST = 1
private const val CONTENT_STYLE_GRID = 2

private const val USER_AGENT = "music.time"