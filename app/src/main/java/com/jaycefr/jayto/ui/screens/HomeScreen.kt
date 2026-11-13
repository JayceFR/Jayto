package com.jaycefr.jayto.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.jaycefr.jayto.domain.model.Song
import com.jaycefr.jayto.domain.repository.Playlist
import com.jaycefr.jayto.ui.viewmodel.AlbumsViewModel
import com.jaycefr.jayto.ui.viewmodel.ArtSearchState
import com.jaycefr.jayto.ui.viewmodel.ArtistsViewModel
import com.jaycefr.jayto.ui.viewmodel.MainCategory
import com.jaycefr.jayto.ui.viewmodel.PlaylistsViewModel
import com.jaycefr.jayto.ui.viewmodel.SongsViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    songsViewModel: SongsViewModel,
    playlistsViewModel: PlaylistsViewModel,
    albumsViewModel: AlbumsViewModel,
    artistsViewModel: ArtistsViewModel,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit
) {
    val pagedSongs = songsViewModel.pagedSongs.collectAsLazyPagingItems()
    val searchQuery by songsViewModel.searchQuery.collectAsState()
    val recentlyPlayed by songsViewModel.recentlyPlayed.collectAsState()
    val selectedCategory by songsViewModel.selectedCategory.collectAsState()
    val selectedSongForPlaylist by songsViewModel.selectedSongForPlaylist.collectAsState()
    val playlists by songsViewModel.playlists.collectAsState()
    val selectedSongs by songsViewModel.selectedSongs.collectAsState()
    val isMultiSelectMode by songsViewModel.isMultiSelectMode.collectAsState()
    val artSearchState by songsViewModel.artSearchState.collectAsState()
    val currentPlayingSongId by songsViewModel.currentSongId.collectAsState()

    val albums by albumsViewModel.albums.collectAsState()
    val artists by artistsViewModel.artists.collectAsState()
    val playlistList by playlistsViewModel.playlists.collectAsState()

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val headerOffset = if (selectedCategory == MainCategory.SONGS && recentlyPlayed.isNotEmpty()) 1 else 0
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = from.index - headerOffset
        val toIndex = to.index - headerOffset
        val count = pagedSongs.itemCount
        if (fromIndex in 0 until count && toIndex in 0 until count) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(Unit) {
        songsViewModel.scanSongs()
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        playlistsViewModel.createPlaylist(newPlaylistName)
                        showCreatePlaylistDialog = false
                        newPlaylistName = ""
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(
            left = 0,
            top = 0,
            right = 0,
            bottom = 0
        ),
        topBar = {
            if (isMultiSelectMode) {
                MultiSelectTopBar(
                    selectedCount = selectedSongs.size,
                    onClose = { songsViewModel.exitMultiSelectMode() },
                    onAddToPlaylist = { songsViewModel.showMultiSelectAddToPlaylist() },
                    onAddToQueue = { songsViewModel.addSelectedToQueue() },
                    onRemove = { songsViewModel.hideSelectedSongs() }
                )
            } else if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { songsViewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search songs, artists, albums...", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { songsViewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            songsViewModel.updateSearchQuery("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("Jayto Music", style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedCategory == MainCategory.PLAYLISTS) {
                FloatingActionButton(onClick = { showCreatePlaylistDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Playlist")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MainCategory.entries.toTypedArray()) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { songsViewModel.selectCategory(category) },
                        label = { Text(category.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }

            when (selectedCategory) {
                MainCategory.SONGS -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (recentlyPlayed.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "Recently Played",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(recentlyPlayed.withIndex().toList()) { (index, song) ->
                                            SongCard(
                                                song = song,
                                                onClick = { songsViewModel.playSongs(recentlyPlayed, index) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "All Songs",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                            }
                        }

                        items(
                            count = pagedSongs.itemCount,
                            key = pagedSongs.itemKey { it.id }
                        ) { index ->
                            val song = pagedSongs[index] ?: return@items
                            val isSelected = selectedSongs.contains(song.id)

                            ReorderableItem(reorderableState, key = song.id) { isDragging ->
                                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                                val backgroundColor = if (isDragging) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else if (currentPlayingSongId == song.id) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.background
                                }

                                Surface(
                                    shadowElevation = elevation,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = backgroundColor
                                ) {
                                    Column {
                                        SongItem(
                                            song = song,
                                            currentPlayingSong = currentPlayingSongId == song.id,
                                            onClick = {
                                                if (isMultiSelectMode) {
                                                    songsViewModel.toggleSelection(song.id)
                                                } else {
                                                    songsViewModel.playSongInWindow(song, index)
                                                }
                                            },
                                            onHide = { songsViewModel.hideSong(song) },
                                            onAddToQueue = { songsViewModel.addSongToQueue(song) },
                                            onAddToPlaylist = { songsViewModel.showAddToPlaylistDialog(song) },
                                            onSearchArt = { songsViewModel.searchAlbumArt(song) },
                                            onLongClick = {
                                                if (!isMultiSelectMode) {
                                                    songsViewModel.enterMultiSelectMode(song.id)
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            },
                                            isSelected = isSelected,
                                            draggableModifier = Modifier.longPressDraggableHandle(
                                                onDragStarted = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            )
                                        )
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                            thickness = 0.5.dp,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                MainCategory.PLAYLISTS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(playlistList) { playlist ->
                            PlaylistItem(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                        }
                    }
                }

                MainCategory.ARTISTS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(artists) { artist ->
                            ArtistItem(artist = artist, onClick = { onArtistClick(artist) })
                        }
                    }
                }

                MainCategory.ALBUMS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(albums) { album ->
                            AlbumItem(album = album, onClick = { onAlbumClick(album) })
                        }
                    }
                }
            }
        }

        if (selectedSongForPlaylist != null) {
            AddToPlaylistDialog(
                playlists = playlists,
                onDismiss = { songsViewModel.dismissAddToPlaylistDialog() },
                onPlaylistSelected = {
                    if (isMultiSelectMode) {
                        songsViewModel.addSelectedToPlaylist(it)
                    } else {
                        songsViewModel.addSongToPlaylist(it)
                    }
                }
            )
        }

        ArtSearchDialog(
            state = artSearchState,
            onDismiss = { songsViewModel.dismissArtSearch() },
            onArtSelected = { songId, url -> songsViewModel.selectAlbumArt(songId, url) },
            onPerformSearch = { song, title, artist -> songsViewModel.performArtSearch(song, title, artist) }
        )
    }
}

@Composable
fun SongCard(song: Song, onClick: () -> Unit) {
    val defaultArt = androidx.compose.ui.res.painterResource(id = com.jaycefr.jayto.R.drawable.ic_default_artwork)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = song.artworkUri ?: com.jaycefr.jayto.R.drawable.ic_default_artwork,
                placeholder = defaultArt,
                error = defaultArt,
                fallback = defaultArt,
                contentDescription = null,
                modifier = Modifier
                    .size(134.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = song.title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
            Text(text = song.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
        }
    }
}
