package com.jaycefr.jayto.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.view.KeyEvent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.jaycefr.jayto.MainActivity
import com.jaycefr.jayto.R
import com.jaycefr.jayto.domain.model.toMediaItem
import com.jaycefr.jayto.domain.repository.SongRepository
import com.jaycefr.jayto.widget.NowPlayingWidgetUpdater
import com.jaycefr.jayto.widget.WidgetActions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MediaLibraryService : MediaLibraryService() {

    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var songRepository: SongRepository

    @Inject
    lateinit var widgetUpdater: NowPlayingWidgetUpdater

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var session: MediaLibrarySession? = null

    private val callback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId("ROOT")
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        @UnstableApi
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controllerInfo: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val player = mediaSession.player
            if (player.mediaItemCount > 0) {
                val currentItemIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                val currentPosition = player.currentPosition.coerceAtLeast(0L)
                val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        items,
                        currentItemIndex,
                        currentPosition
                    )
                )
            }

            val settableFuture = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                try {
                    val recentSongs = songRepository.getRecentlyPlayed().first()
                    val songsToUse = if (recentSongs.isNotEmpty()) recentSongs else songRepository.getAllSongs().first()
                    if (songsToUse.isNotEmpty()) {
                        val mediaItems = songsToUse.map { it.toMediaItem() }
                        settableFuture.set(
                            MediaSession.MediaItemsWithStartPosition(
                                mediaItems,
                                0,
                                0L
                            )
                        )
                    } else {
                        settableFuture.setException(UnsupportedOperationException("No songs found"))
                    }
                } catch (e: Exception) {
                    settableFuture.setException(e)
                }
            }
            return settableFuture
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                .build()
            
            return MediaSession.ConnectionResult.accept(
                availableSessionCommands,
                connectionResult.availablePlayerCommands
            )
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        /* User swiped the app away from the recently used list. Completely closed the app */
        super.onTaskRemoved(rootIntent)
        // Samsung & co. often kill the process right after this; update the widget first.
        widgetUpdater.detach(renderNow = true)
        val player = session?.player ?: return
        player.pause()
        player.stop()
        stopSelf()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val notificationProvider = DefaultMediaNotificationProvider(this)
        notificationProvider.setSmallIcon(R.drawable.jayto_bitmap)
        setMediaNotificationProvider(notificationProvider)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Fresh player per service lifetime (see PlaybackManager.exoPlayer).
        val player = playbackManager.exoPlayer
        session = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(pendingIntent)
            .build()

        widgetUpdater.attach(player)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == WidgetActions.ACTION_PLAY_RECENT) {
            playRecentFromWidget(intent.getLongExtra(WidgetActions.EXTRA_SONG_ID, -1L))
            return START_NOT_STICKY
        }
        // Media buttons (widget Play, notification, headset) are handled by Media3.
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * A "recently played" tile on the widget was tapped. We were started as a foreground service,
     * so playback must begin promptly for Media3 to post its notification: queue the recents list
     * from the tapped song (same as the in-app row). If the song is gone, resume instead of
     * leaving a foreground start unfulfilled.
     */
    private fun playRecentFromWidget(songId: Long) {
        serviceScope.launch {
            val recents = runCatching { songRepository.getRecentlyPlayed().first() }.getOrDefault(emptyList())
            val index = recents.indexOfFirst { it.id == songId }
            // The session's own player: never a released one, never a stray new one.
            val player = session?.player ?: return@launch
            if (index >= 0) {
                // On a cold start no controller has connected yet, so the session isn't tracked by
                // Media3's notification manager; without this no foreground notification is posted.
                // Idempotent.
                session?.let { addSession(it) }
                player.setMediaItems(recents.map { it.toMediaItem() }, index, 0L)
                player.prepare()
                player.play()
            } else {
                val resume = Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setComponent(ComponentName(this@MediaLibraryService, MediaLibraryService::class.java))
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                super.onStartCommand(resume, 0, 0)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return session
    }

    override fun onDestroy() {
        // Before the player is released: freeze the widget on the last song, shown as resumable.
        widgetUpdater.detach(renderNow = true)
        serviceScope.cancel()
        session?.let {
            it.release()
            playbackManager.release()
        }
        session = null
        super.onDestroy()
    }
}
