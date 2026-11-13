package com.jaycefr.jayto.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.jaycefr.jayto.domain.model.toMediaItem
import com.jaycefr.jayto.domain.repository.SongRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class MediaControllerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controller: MediaController? = null
    private var pendingUri: Uri? = null

    private data class SongWindow(val query: String, var nextOffset: Int, val firstMediaId: String?)

    private var window: SongWindow? = null
    private var prefetchJob: Job? = null

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
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { 
                    updateState() 
                    checkPrefetch()
                }
                override fun onPlaybackStateChanged(state: Int) { updateState() }
                override fun onIsPlayingChanged(playing: Boolean) { updateState() }
                override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                    updateState()
                    checkPrefetch() // what's "upcoming" changes with the play order
                }
                override fun onRepeatModeChanged(mode: Int) { updateState() }
                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    // The queue can be replaced outside this class (e.g. the home-screen widget
                    // starting a "recent" queue in the service). Appends keep the first item, so a
                    // different first item means our library window no longer owns the queue.
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                        val current = window
                        val first = if (player.mediaItemCount > 0) player.getMediaItemAt(0).mediaId else null
                        if (current != null && current.firstMediaId != first) clearWindow()
                    }
                    updateState()
                }
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) { updateState() }
            })

            pendingUri?.let { uri ->
                playMediaUri(uri)
                pendingUri = null
            }
        }
    }

    private fun updateState() {
        controller?.let { player ->
            val queueItems = mutableListOf<MediaItem>()
            for (i in 0 until player.mediaItemCount) {
                queueItems.add(player.getMediaItemAt(i))
            }
            _state.update {
                it.copy(
                    currentSong = player.currentMediaItem,
                    isPlaying = player.isPlaying,
                    playbackState = player.playbackState,
                    duration = player.duration.coerceAtLeast(0L),
                    shuffleModeEnabled = player.shuffleModeEnabled,
                    repeatMode = player.repeatMode,
                    hasNext = player.hasNextMediaItem(),
                    hasPrevious = player.hasPreviousMediaItem(),
                    queue = queueItems,
                    currentMediaItemIndex = player.currentMediaItemIndex
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
                delay(500.milliseconds)
            }
        }
    }

    fun play() { controller?.play() }
    fun pause() { controller?.pause() }
    fun skipToNext() { controller?.seekToNext() }
    fun skipToPrevious() { controller?.seekToPrevious() }
    fun seekTo(position: Long) { controller?.seekTo(position) }

    fun toggleShuffle() {
        controller?.let { player ->
            val newShuffleMode = !player.shuffleModeEnabled
            player.shuffleModeEnabled = newShuffleMode
            _state.update { it.copy(shuffleModeEnabled = newShuffleMode) }
        }
    }

    fun toggleRepeat() {
        controller?.let { player ->
            val nextRepeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            player.repeatMode = nextRepeatMode
            _state.update { it.copy(repeatMode = nextRepeatMode) }
        }
    }

    fun addToQueue(mediaItem: MediaItem) {
        controller?.let { player ->
            if (player.mediaItemCount == 0) {
                playMediaItem(mediaItem)
            } else {
                player.addMediaItem(mediaItem)
                updateState()
            }
        }
    }

    fun addToQueue(mediaItems: List<MediaItem>) {
        controller?.let { player ->
            if (player.mediaItemCount == 0) {
                playMediaItems(mediaItems, 0)
            } else {
                player.addMediaItems(mediaItems)
                updateState()
            }
        }
    }

    fun removeFromQueue(index: Int) {
        controller?.let { player ->
            if (index in 0 until player.mediaItemCount) {
                player.removeMediaItem(index)
                updateState()
            }
        }
    }

    fun seekToQueueIndex(index: Int) {
        controller?.let { player ->
            if (index in 0 until player.mediaItemCount) {
                player.seekToDefaultPosition(index)
                player.play()
                updateState()
            }
        }
    }

    fun playMediaUri(uri: Uri) {
        val metadata = getMediaMetadataFromUri(uri)
        val mediaItem = MediaItem.Builder()
            .setMediaId(uri.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()

        val player = controller
        if (player != null) {
            clearWindow()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        } else {
            pendingUri = uri
        }
    }

    private fun getMediaMetadataFromUri(uri: Uri): MediaMetadata {
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        if (title.isNullOrBlank() && uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            title = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (title.isNullOrBlank()) {
            title = uri.lastPathSegment ?: "Unknown Track"
        }
        
        if (title.contains(".")) {
            title = title.substringBeforeLast(".")
        }

        return MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist ?: "<Unknown>")
            .setAlbumTitle(album ?: "<Unknown>")
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
    }

    fun playMediaItem(mediaItem: MediaItem) {
        controller?.let {
            clearWindow()
            it.setMediaItem(mediaItem)
            it.prepare()
            it.play()
        }
    }

    fun playMediaItems(mediaItems: List<MediaItem>, index: Int) {
        controller?.let {
            clearWindow()
            it.setMediaItems(mediaItems, index, 0L)
            it.prepare()
            it.play()
        }
    }

    /**
     * Plays a slice of the library. [query] is the search the slice came from; [nextOffset] is the
     * library position right after the slice. More songs from the same query are appended as the
     * queue nears its end.
     */
    fun playWindowedSongs(mediaItems: List<MediaItem>, initialIndex: Int, nextOffset: Int, query: String) {
        playMediaItems(mediaItems, initialIndex) // clears any previous window
        if (controller != null) {
            window = SongWindow(query, nextOffset, mediaItems.firstOrNull()?.mediaId)
        }
    }

    private fun clearWindow() {
        window = null
        prefetchJob?.cancel()
        prefetchJob = null
    }

    private fun checkPrefetch() {
        val player = controller ?: return
        val currentWindow = window ?: return
        if (prefetchJob?.isActive == true) return

        if (upcomingCount(player) > PREFETCH_THRESHOLD) return

        prefetchJob = scope.launch {
            try {
                val nextBatch = withContext(Dispatchers.IO) {
                    songRepository.getSongsWindow(currentWindow.nextOffset, PREFETCH_BATCH_SIZE, currentWindow.query)
                        .map { it.toMediaItem() }
                }
                // Playback may have been replaced while we were querying.
                if (window !== currentWindow || nextBatch.isEmpty()) return@launch
                currentWindow.nextOffset += nextBatch.size
                player.addMediaItems(nextBatch)
                updateState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                return@launch
            }
            // The user may have skipped several songs while we were loading.
            checkPrefetchAfter(currentWindow)
        }
    }

    private fun checkPrefetchAfter(previousWindow: SongWindow) {
        scope.launch {
            if (window === previousWindow) checkPrefetch()
        }
    }

    private fun upcomingCount(player: Player): Int {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return 0
        val shuffle = player.shuffleModeEnabled
        var index = player.currentMediaItemIndex
        var count = 0
        while (count <= PREFETCH_THRESHOLD) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
            if (index == androidx.media3.common.C.INDEX_UNSET) break
            count++
        }
        return count
    }

    fun updateMediaItemMetadata(updatedItem: MediaItem) {
        controller?.let { player ->
            for (i in 0 until player.mediaItemCount) {
                if (player.getMediaItemAt(i).mediaId == updatedItem.mediaId) {
                    player.replaceMediaItem(i, updatedItem)
                    break
                }
            }
        }
    }

    private companion object {
        const val PREFETCH_THRESHOLD = 3
        const val PREFETCH_BATCH_SIZE = 10
    }
}
