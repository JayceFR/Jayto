package com.jaycefr.jayto.playback

import android.app.PendingIntent
import android.content.Intent
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

        session = MediaLibrarySession.Builder(this, playbackManager.exoPlayer, callback)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return session
    }

    override fun onDestroy() {
        serviceScope.cancel()
        session?.let {
            it.release()
            playbackManager.release()
        }
        session = null
        super.onDestroy()
    }
}
