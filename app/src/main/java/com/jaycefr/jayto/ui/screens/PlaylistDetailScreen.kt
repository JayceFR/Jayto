package com.jaycefr.jayto.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jaycefr.jayto.domain.model.Song
import com.jaycefr.jayto.ui.viewmodel.PlaylistDetailViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel
) {
    val songs by viewModel.songs.collectAsState()
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.moveSong(from.index, to.index)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(viewModel.playlistName) }) }
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                ReorderableItem(reorderableState, key = song.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                    
                    Surface(
                        shadowElevation = elevation,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            SongItem(
                                song = song,
                                onClick = { viewModel.playSong(song) },
                                onHide = { viewModel.hideSong(song) },
                                onLongClick = {},
                                draggableModifier = Modifier.longPressDraggableHandle()
                            )
                        }
                    }
                }
            }
        }
    }
}
