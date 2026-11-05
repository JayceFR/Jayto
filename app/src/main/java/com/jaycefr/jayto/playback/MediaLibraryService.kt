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
import com.jaycefr.jayto.MainActivity
import com.jaycefr.jayto.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MediaLibraryService : MediaLibraryService() {

    @Inject
    lateinit var playbackManager: PlaybackManager

    private var session: MediaLibrarySession? = null

    private val callback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Return root item for browsing
            val rootItem = MediaItem.Builder()
                .setMediaId("ROOT")
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }
        
        // TODO: Implement onGetChildren for browsing

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                // Add any custom session commands here if needed
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
        player.stop()
        player.clearMediaItems()
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
        session?.let {
            it.release()
            playbackManager.release()
        }
        session = null
        super.onDestroy()
    }
}
