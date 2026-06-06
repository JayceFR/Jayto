package com.jaycefr.jayto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaycefr.jayto.domain.model.Song
import com.jaycefr.jayto.domain.model.toMediaItem
import com.jaycefr.jayto.domain.repository.PlaylistRepository
import com.jaycefr.jayto.domain.repository.SongRepository
import com.jaycefr.jayto.playback.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongsViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val mediaControllerManager: MediaControllerManager
) : ViewModel() {

    val songs = songRepository.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _reorderedSongs = MutableStateFlow<List<Song>?>(null)
    val displaySongs = combine(songs, _reorderedSongs) { original, reordered ->
        reordered ?: original
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists = playlistRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSongForPlaylist = MutableStateFlow<Song?>(null)
    val selectedSongForPlaylist = _selectedSongForPlaylist.asStateFlow()

    val state = mediaControllerManager.state

    fun scanSongs() {
        viewModelScope.launch {
            songRepository.scanLocalSongs()
        }
    }

    fun playSong(song: Song) {
        mediaControllerManager.playMediaItem(song.toMediaItem())
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        mediaControllerManager.playMediaItems(songs.map { it.toMediaItem() }, startIndex)
    }

    fun hideSong(song: Song) {
        viewModelScope.launch {
            songRepository.toggleHidden(song.id, true)
        }
    }

    fun moveSong(fromIndex: Int, toIndex: Int) {
        val currentList = displaySongs.value.toMutableList()
        val song = currentList.removeAt(fromIndex)
        currentList.add(toIndex, song)
        _reorderedSongs.value = currentList

        viewModelScope.launch {
            songRepository.reorderSongs(currentList.map { it.id })
        }
    }

    fun showAddToPlaylistDialog(song: Song) {
        _selectedSongForPlaylist.value = song
    }

    fun dismissAddToPlaylistDialog() {
        _selectedSongForPlaylist.value = null
    }

    fun addSongToPlaylist(playlistId: Long) {
        val song = _selectedSongForPlaylist.value ?: return
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song.id)
            dismissAddToPlaylistDialog()
        }
    }
}
