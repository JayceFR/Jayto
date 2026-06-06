package com.jaycefr.jayto.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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

    override fun onCreate() {
        super.onCreate()
        session = MediaLibrarySession.Builder(this, playbackManager.exoPlayer, callback).build()
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
