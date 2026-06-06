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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.moveSong(from.index, to.index)
    }

    LaunchedEffect(Unit) {
        viewModel.scanSongs()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Songs") })
        }
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(songs.withIndex().toList(), key = { it.value.id }) { (index, song) ->
                ReorderableItem(reorderableState, key = song.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)

                    Surface(
                        shadowElevation = elevation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SongItem(
                            song = song,
                            onClick = { viewModel.playSongs(songs, index) },
                            onHide = { viewModel.hideSong(song) },
                            onAddToPlaylist = { viewModel.showAddToPlaylistDialog(song) },
                            isReorderable = true,
                            draggableModifier = Modifier.draggableHandle()
                        )
                    }
                }
            }
        }

        if (selectedSongForPlaylist != null) {
            AddToPlaylistDialog(
                playlists = playlists,
                onDismiss = { viewModel.dismissAddToPlaylistDialog() },
                onPlaylistSelected = { viewModel.addSongToPlaylist(it) }
            )
        }
    }
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
    isReorderable: Boolean = false,
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
                        onLongPress = { showMenu = true }
                    )
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isReorderable) {
                IconButton(
                    modifier = draggableModifier,
                    onClick = {}
                ) {
                    Icon(Icons.Default.DragHandle, contentDescription = "Reorder")
                }
            }

            AsyncImage(
                model = song.artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = song.title, style = MaterialTheme.typography.titleMedium)
                Text(text = song.artist, style = MaterialTheme.typography.bodyMedium)
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
