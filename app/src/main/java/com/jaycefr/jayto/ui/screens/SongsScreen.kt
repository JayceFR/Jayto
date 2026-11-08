package com.jaycefr.jayto.ui.screens

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.jaycefr.jayto.domain.model.Song
import com.jaycefr.jayto.ui.viewmodel.ArtSearchState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
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
            IconButton(onClick = onAddToQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Add to Queue")
            }
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
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onSearchArt: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    currentPlayingSong: Boolean = false,
    draggableModifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val itemBackgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        currentPlayingSong -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(itemBackgroundColor)
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
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RotatingArtwork(
                artworkUri = song.artworkUri,
                isPlaying = currentPlayingSong,
                modifier = draggableModifier
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (currentPlayingSong){
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                            velocity = 40.dp
                        )
                    )
                }
                else{
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
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
            if (onAddToQueue != null) {
                DropdownMenuItem(
                    text = { Text("Add to Queue") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                    onClick = { 
                        showMenu = false
                        onAddToQueue()
                    }
                )
            }
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

@Composable
fun RotatingArtwork(
    artworkUri: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val defaultArt = painterResource(
        id = com.jaycefr.jayto.R.drawable.ic_default_artwork
    )

    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = remember(size, density) { with(density) { size.roundToPx() } }

    val imageRequest = remember(artworkUri, sizePx) {
        ImageRequest.Builder(context)
            .data(artworkUri ?: com.jaycefr.jayto.R.drawable.ic_default_artwork)
            .size(sizePx, sizePx)
            .crossfade(true)
            .build()
    }

    val rotation = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "record_rotation")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        angle
    } else 0f

    AsyncImage(
        model = imageRequest,
        placeholder = defaultArt,
        error = defaultArt,
        fallback = defaultArt,
        contentDescription = "Album artwork",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .graphicsLayer {
                rotationZ = rotation
            }
    )
}