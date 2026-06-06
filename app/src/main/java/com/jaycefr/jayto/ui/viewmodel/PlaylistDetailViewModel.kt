package com.jaycefr.jayto.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaycefr.jayto.domain.model.Song
import com.jaycefr.jayto.domain.model.toMediaItem
import com.jaycefr.jayto.domain.repository.PlaylistRepository
import com.jaycefr.jayto.playback.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val mediaControllerManager: MediaControllerManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])
    val playlistName: String = savedStateHandle["playlistName"] ?: "Playlist"

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getSongsForPlaylist(playlistId).collect {
                _songs.value = it
            }
        }
    }

    fun playSong(song: Song) {
        val index = _songs.value.indexOf(song)
        if (index != -1) {
            mediaControllerManager.playMediaItems(_songs.value.map { it.toMediaItem() }, index)
        }
    }

    fun moveSong(fromIndex: Int, toIndex: Int) {
        val currentList = _songs.value.toMutableList()
        val song = currentList.removeAt(fromIndex)
        currentList.add(toIndex, song)
        _songs.value = currentList
        
        viewModelScope.launch {
            playlistRepository.reorderPlaylist(playlistId, currentList.map { it.id })
        }
    }

    fun hideSong(song: Song) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(playlistId, song.id)
        }
    }
}
