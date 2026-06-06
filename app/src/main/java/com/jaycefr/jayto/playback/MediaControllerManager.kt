package com.jaycefr.jayto.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaControllerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    init {
        val sessionToken = SessionToken(context, ComponentName(context, MediaLibraryService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get()
                setupController()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
        
        startPositionUpdate()
    }

    private fun setupController() {
        controller?.let { player ->
            updateState()
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateState() }
                override fun onPlaybackStateChanged(state: Int) { updateState() }
                override fun onIsPlayingChanged(playing: Boolean) { updateState() }
                override fun onShuffleModeEnabledChanged(enabled: Boolean) { updateState() }
                override fun onRepeatModeChanged(mode: Int) { updateState() }
            })
        }
    }

    private fun updateState() {
        controller?.let { player ->
            _state.update {
                it.copy(
                    currentSong = player.currentMediaItem,
                    isPlaying = player.isPlaying,
                    playbackState = player.playbackState,
                    duration = player.duration.coerceAtLeast(0L),
                    shuffleModeEnabled = player.shuffleModeEnabled,
                    repeatMode = player.repeatMode,
                    hasNext = player.hasNextMediaItem(),
                    hasPrevious = player.hasPreviousMediaItem()
                )
            }
        }
    }

    private fun startPositionUpdate() {
        scope.launch {
            while (isActive) {
                controller?.let { player ->
                    if (player.isPlaying) {
                        _state.update { it.copy(currentPosition = player.currentPosition) }
                    }
                }
                delay(1000)
            }
        }
    }

    fun play() { controller?.play() }
    fun pause() { controller?.pause() }
    fun skipToNext() { controller?.seekToNext() }
    fun skipToPrevious() { controller?.seekToPrevious() }
    fun seekTo(position: Long) { controller?.seekTo(position) }

    fun playMediaItem(mediaItem: MediaItem) {
        controller?.let {
            it.setMediaItem(mediaItem)
            it.prepare()
            it.play()
        }
    }

    fun playMediaItems(mediaItems: List<MediaItem>, index: Int) {
        controller?.let {
            it.setMediaItems(mediaItems, index, 0L)
            it.prepare()
            it.play()
        }
    }
}
