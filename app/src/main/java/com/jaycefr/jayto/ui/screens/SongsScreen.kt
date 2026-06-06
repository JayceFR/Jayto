package com.jaycefr.jayto.ui.screens

import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import coil.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.jaycefr.jayto.domain.model.Song
import com.jaycefr.jayto.ui.viewmodel.ArtSearchState
import com.jaycefr.jayto.ui.viewmodel.SongsViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    viewModel: SongsViewModel
) {
    val songs by viewModel.displaySongs.collectAsState()
    val selectedSongForPlaylist by viewModel.selectedSongForPlaylist.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val selectedSongs by viewModel.selectedSongs.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val artSearchState by viewModel.artSearchState.collectAsState()
    
    val haptic = LocalHapticFeedback.current

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.moveSong(from.index, to.index)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(Unit) {
        viewModel.scanSongs()
    }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                MultiSelectTopBar(
                    selectedCount = selectedSongs.size,
                    onClose = { viewModel.exitMultiSelectMode() },
                    onAddToPlaylist = { viewModel.showMultiSelectAddToPlaylist() },
                    onRemove = { viewModel.hideSelectedSongs() }
                )
            } else {
                TopAppBar(title = { Text("Songs") })
            }
        }
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                val isSelected = selectedSongs.contains(song.id)
                val index = songs.indexOf(song)
                
                ReorderableItem(reorderableState, key = song.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                    val backgroundColor = if (isDragging) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }

                    Surface(
                        shadowElevation = elevation,
                        modifier = Modifier.fillMaxWidth(),
                        color = backgroundColor
                    ) {
                        SongItem(
                            song = song,
                            onClick = { 
                                if (isMultiSelectMode) {
                                    viewModel.toggleSelection(song.id)
                                } else {
                                    viewModel.playSongs(songs, index)
                                }
                            },
                            onHide = { viewModel.hideSong(song) },
                            onAddToPlaylist = { viewModel.showAddToPlaylistDialog(song) },
                            onSearchArt = { viewModel.searchAlbumArt(song) },
                            onLongClick = {
                                if (!isMultiSelectMode) {
                                    viewModel.enterMultiSelectMode(song.id)
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
                    }
                }
            }
        }

        if (selectedSongForPlaylist != null) {
            AddToPlaylistDialog(
                playlists = playlists,
                onDismiss = { viewModel.dismissAddToPlaylistDialog() },
                onPlaylistSelected = { 
                    if (isMultiSelectMode) {
                        viewModel.addSelectedToPlaylist(it)
                    } else {
                        viewModel.addSongToPlaylist(it)
                    }
                }
            )
        }

        ArtSearchDialog(
            state = artSearchState,
            onDismiss = { viewModel.dismissArtSearch() },
            onArtSelected = { songId, url -> viewModel.selectAlbumArt(songId, url) },
            onPerformSearch = { song, title, artist -> viewModel.performArtSearch(song, title, artist) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemove: () -> Unit
) {
    TopAppBar(
        title = { Text("$selectedCount Selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        },
        actions = {
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Playlist")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<com.jaycefr.jayto.domain.repository.Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            if (playlists.isEmpty()) {
                Text("No playlists found.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(playlists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            modifier = Modifier.clickable { onPlaylistSelected(playlist.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ArtSearchDialog(
    state: ArtSearchState,
    onDismiss: () -> Unit,
    onArtSelected: (Long, String) -> Unit,
    onPerformSearch: (Song, String, String) -> Unit
) {
    if (state is ArtSearchState.Idle) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(if (state is ArtSearchState.InputQuery) "Search Album Art" else "Choose Album Art") 
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                when (state) {
                    is ArtSearchState.InputQuery -> {
                        var title by remember { mutableStateOf(state.song.title) }
                        var artist by remember { mutableStateOf(state.song.artist) }
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Song Title") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = artist,
                                onValueChange = { artist = it },
                                label = { Text("Artist") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { onPerformSearch(state.song, title, artist) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Search")
                            }
                        }
                    }
                    is ArtSearchState.Searching -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Searching for ${state.song.title}...")
                            }
                        }
                    }
                    is ArtSearchState.Results -> {
                        if (state.urls.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No images found.")
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.urls) { url ->
                                    Card(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clickable { onArtSelected(state.song.id, url) }
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is ArtSearchState.Downloading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Downloading artwork...")
                            }
                        }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun SongItem(
    song: Song,
    onClick: () -> Unit,
    onHide: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onSearchArt: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    draggableModifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { 
                            if (isSelected) onLongClick() 
                            showMenu = true 
                            onLongClick()
                        }
                    )
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .then(draggableModifier),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = song.title, style = MaterialTheme.typography.titleMedium)
                Text(text = song.artist, style = MaterialTheme.typography.bodyMedium)
            }
            if (isSelected) {
                Checkbox(
                    checked = true,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (onSearchArt != null) {
                DropdownMenuItem(
                    text = { Text("Search for Album Art") },
                    leadingIcon = { Icon(Icons.Default.ImageSearch, contentDescription = null) },
                    onClick = { 
                        showMenu = false
                        onSearchArt()
                    }
                )
            }
            if (onAddToPlaylist != null) {
                DropdownMenuItem(
                    text = { Text("Add to Playlist") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    onClick = { 
                        showMenu = false
                        onAddToPlaylist()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Remove from Library") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onHide()
                }
            )
        }
    }
}
