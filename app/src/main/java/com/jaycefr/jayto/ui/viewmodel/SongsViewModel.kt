package com.jaycefr.jayto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaycefr.jayto.domain.model.Song
import com.jaycefr.jayto.domain.model.toMediaItem
import com.jaycefr.jayto.domain.repository.SongRepository
import com.jaycefr.jayto.playback.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongsViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val mediaControllerManager: MediaControllerManager
) : ViewModel() {

    val songs = songRepository.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val state = mediaControllerManager.state

    fun scanSongs() {
        viewModelScope.launch {
            songRepository.scanLocalSongs()
        }
    }

    fun playSong(song: Song) {
        mediaControllerManager.playMediaItem(song.toMediaItem())
    }
}
