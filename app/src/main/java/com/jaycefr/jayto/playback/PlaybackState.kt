package com.jaycefr.jayto.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

data class PlaybackState(
    val currentSong: MediaItem? = null,
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)
