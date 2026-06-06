package com.jaycefr.jayto.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaycefr.jayto.domain.model.Song
import com.jaycefr.jayto.domain.model.toMediaItem
import com.jaycefr.jayto.domain.repository.SongRepository
import com.jaycefr.jayto.playback.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val mediaControllerManager: MediaControllerManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val artistName: String = checkNotNull(savedStateHandle["artistName"])

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    init {
        viewModelScope.launch {
            songRepository.getSongsByArtist(artistName).collect {
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
}
