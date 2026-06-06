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
        if (reordered != null && reordered.size == original.size) reordered else original
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSongs = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongs = _selectedSongs.asStateFlow()

    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode = _isMultiSelectMode.asStateFlow()

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
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        
        val song = currentList.removeAt(fromIndex)
        currentList.add(toIndex, song)
        _reorderedSongs.value = currentList

        viewModelScope.launch {
            songRepository.reorderSongs(currentList.map { it.id })
        }
    }

    fun toggleSelection(songId: Long) {
        _selectedSongs.update { current ->
            if (current.contains(songId)) {
                val next = current - songId
                if (next.isEmpty()) _isMultiSelectMode.value = false
                next
            } else {
                _isMultiSelectMode.value = true
                current + songId
            }
        }
    }

    fun enterMultiSelectMode(firstSongId: Long) {
        _isMultiSelectMode.value = true
        _selectedSongs.value = setOf(firstSongId)
    }

    fun exitMultiSelectMode() {
        _isMultiSelectMode.value = false
        _selectedSongs.value = emptySet()
    }

    fun hideSelectedSongs() {
        val ids = _selectedSongs.value
        viewModelScope.launch {
            ids.forEach { songRepository.toggleHidden(it, true) }
            exitMultiSelectMode()
        }
    }

    fun addSelectedToPlaylist(playlistId: Long) {
        val ids = _selectedSongs.value
        viewModelScope.launch {
            ids.forEach { playlistRepository.addSongToPlaylist(playlistId, it) }
            exitMultiSelectMode()
            dismissAddToPlaylistDialog()
        }
    }

    fun showMultiSelectAddToPlaylist() {
        if (_selectedSongs.value.isNotEmpty()) {
            _selectedSongForPlaylist.value = Song(
                id = -1, title = "", artist = "", album = "", duration = 0,
                fileUri = "", artworkUri = null, trackNumber = null, discNumber = null,
                dateAdded = 0, dateModified = 0, playCount = 0, lastPlayed = null,
                isFavorite = false, isHidden = false, customOrder = 0
            )
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
