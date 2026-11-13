package com.jaycefr.jayto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.jaycefr.jayto.domain.model.Song
import com.jaycefr.jayto.domain.model.toMediaItem
import com.jaycefr.jayto.domain.repository.PlaylistRepository
import com.jaycefr.jayto.domain.repository.SongRepository
import com.jaycefr.jayto.playback.MediaControllerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class SongsViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val mediaControllerManager: MediaControllerManager
) : ViewModel() {

    val recentlyPlayed = songRepository.getRecentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums = songRepository.getAllAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists = songRepository.getAllArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCategory = MutableStateFlow(MainCategory.SONGS)
    val selectedCategory = _selectedCategory.asStateFlow()

    fun selectCategory(category: MainCategory) {
        _selectedCategory.value = category
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedSongs: Flow<PagingData<Song>> = _searchQuery
        .debounce { if (it.isBlank()) 0L else 250L }
        .flatMapLatest { query ->
            songRepository.getPagedSongs(query)
        }
        .cachedIn(viewModelScope)

    private val _selectedSongs = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongs = _selectedSongs.asStateFlow()

    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode = _isMultiSelectMode.asStateFlow()

    val playlists = playlistRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSongForPlaylist = MutableStateFlow<Song?>(null)
    val selectedSongForPlaylist = _selectedSongForPlaylist.asStateFlow()

    private val _artSearchState = MutableStateFlow<ArtSearchState>(ArtSearchState.Idle)
    val artSearchState = _artSearchState.asStateFlow()

    val currentSongId: StateFlow<Long?> =
        mediaControllerManager.state
            .map { playbackState ->
                playbackState.currentSong?.mediaId?.toLongOrNull()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )

    fun scanSongs() {
        viewModelScope.launch {
            songRepository.scanLocalSongs()
        }
    }

    fun playSong(song: Song) {
        mediaControllerManager.playMediaItem(song.toMediaItem())
    }

    /**
     * Plays [song] with a small slice of the current (possibly searched) library around it.
     * The slice position is looked up from the database by the song's sort key rather than taken
     * from the paged list index, which stops matching DB offsets after a paging refresh.
     */
    fun playSongInWindow(song: Song) {
        val query = searchQuery.value
        viewModelScope.launch(Dispatchers.IO) {
            val position = songRepository.getSongPosition(song, query)
            val windowOffset = (position - WINDOW_SONGS_BEFORE).coerceAtLeast(0)
            val windowSongs = songRepository.getSongsWindow(windowOffset, WINDOW_SIZE, query)

            val playingIndex = windowSongs.indexOfFirst { it.id == song.id }
            val items: List<Song>
            val startIndex: Int
            if (playingIndex >= 0) {
                items = windowSongs
                startIndex = playingIndex
            } else {
                // Song vanished from the query between tap and lookup (hidden, rescanned, ...).
                // Still play what the user tapped instead of a neighbour.
                items = listOf(song) + windowSongs.filter { it.id != song.id }
                startIndex = 0
            }

            withContext(Dispatchers.Main) {
                mediaControllerManager.playWindowedSongs(
                    mediaItems = items.map { it.toMediaItem() },
                    initialIndex = startIndex,
                    nextOffset = windowOffset + windowSongs.size,
                    query = query
                )
            }
        }
    }

    fun addSongToQueue(song: Song) {
        mediaControllerManager.addToQueue(song.toMediaItem())
    }

    fun addSelectedToQueue() {
        val selectedIds = _selectedSongs.value.toList()
        if (selectedIds.isNotEmpty()) {
            viewModelScope.launch {
                val selectedSongList = selectedIds.mapNotNull { songRepository.getSongById(it) }
                if (selectedSongList.isNotEmpty()) {
                    mediaControllerManager.addToQueue(selectedSongList.map { it.toMediaItem() })
                    exitMultiSelectMode()
                }
            }
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        mediaControllerManager.playMediaItems(songs.map { it.toMediaItem() }, startIndex)
    }

    fun hideSong(song: Song) {
        viewModelScope.launch {
            songRepository.toggleHidden(song.id, true)
        }
    }

    fun searchAlbumArt(song: Song) {
        _artSearchState.value = ArtSearchState.InputQuery(song)
    }

    fun performArtSearch(song: Song, title: String, artist: String) {
        _artSearchState.value = ArtSearchState.Searching(song)
        viewModelScope.launch {
            val query = if (artist.isBlank()) title else "recording:\"$title\" AND artist:\"$artist\""
            // We pass the refined query to the fetcher. 
            // Note: AlbumArtFetcher needs to handle this new query format for MusicBrainz
            val urls = songRepository.getArtUrls(song.copy(title = title, artist = artist))
            _artSearchState.value = ArtSearchState.Results(song, urls)
        }
    }

    fun selectAlbumArt(songId: Long, imageUrl: String) {
        _artSearchState.value = ArtSearchState.Downloading
        viewModelScope.launch {
            val newUri = songRepository.downloadArt(songId, imageUrl)
            if (newUri != null) {
                // Find the song in current list and update its MediaItem if it's being played
                val updatedSong = songRepository.getSongById(songId)
                updatedSong?.let {
                    mediaControllerManager.updateMediaItemMetadata(it.toMediaItem())
                }
            }
            _artSearchState.value = ArtSearchState.Idle
        }
    }

    fun dismissArtSearch() {
        _artSearchState.value = ArtSearchState.Idle
    }

    /** Moves [fromId] into [toId]'s slot. Suspends until the DB write is done so drag-reorder can await it. */
    suspend fun moveSong(fromId: Long, toId: Long) {
        songRepository.moveSong(fromId, toId)
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

sealed class ArtSearchState {
    object Idle : ArtSearchState()
    data class InputQuery(val song: Song) : ArtSearchState()
    data class Searching(val song: Song) : ArtSearchState()
    data class Results(val song: Song, val urls: List<String>) : ArtSearchState()
    object Downloading : ArtSearchState()
}

private const val WINDOW_SONGS_BEFORE = 5
private const val WINDOW_SIZE = 16

enum class MainCategory(val displayName: String) {
    SONGS("Songs"),
    PLAYLISTS("Playlists"),
    ARTISTS("Artists"),
    ALBUMS("Albums")
}
