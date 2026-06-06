package com.jaycefr.jayto.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.automirrored.filled.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import com.jaycefr.jayto.domain.model.Song
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
fun SongItem(
    song: Song,
    onClick: () -> Unit,
    onHide: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
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
                            if (isSelected) onLongClick() // Or context menu if already selected?
                            // Actually, let's keep it simple: 
                            // If NOT in multi-select, show menu. 
                            // If IN multi-select, just toggle (handled by onClick).
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
            if (onAddToPlaylist != null) {
                DropdownMenuItem(
                    text = { Text("Add to Playlist") },
                    onClick = { 
                        showMenu = false
                        onAddToPlaylist()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Remove from Library") },
                onClick = {
                    showMenu = false
                    onHide()
                }
            )
        }
    }
}
