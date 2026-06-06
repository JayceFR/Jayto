package com.jaycefr.jayto.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jaycefr.jayto.domain.repository.SongRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        setAudioAttributes(audioAttributes, true)
    }

    private val _currentSong = MutableStateFlow<MediaItem?>(null)
    val currentSong: StateFlow<MediaItem?> = _currentSong.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentSong.value = mediaItem
                mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                    scope.launch {
                        songRepository.incrementPlayCount(id)
                    }
                }
            }
        })
    }

    fun playSong(mediaItem: MediaItem) {
        if (!verifyFileExists(mediaItem)) return
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun playSongs(mediaItems: List<MediaItem>, startIndex: Int = 0) {
        val validItems = mediaItems.filter { verifyFileExists(it) }
        if (validItems.isEmpty()) return
        
        val adjustedIndex = if (startIndex < mediaItems.size) {
            val targetUri = mediaItems[startIndex].localConfiguration?.uri
            validItems.indexOfFirst { it.localConfiguration?.uri == targetUri }.coerceAtLeast(0)
        } else 0

        exoPlayer.setMediaItems(validItems, adjustedIndex, 0L)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    private fun verifyFileExists(mediaItem: MediaItem): Boolean {
        val uri = mediaItem.localConfiguration?.uri ?: return false
        return if (uri.scheme == "file" || uri.scheme == null) {
            java.io.File(uri.path ?: "").exists()
        } else true // Assume content URIs are handled by ExoPlayer's error listener
    }

    fun release() {
        scope.cancel()
        exoPlayer.release()
    }
}
