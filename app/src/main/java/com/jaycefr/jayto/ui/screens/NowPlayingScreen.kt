package com.jaycefr.jayto.ui.screens

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.jaycefr.jayto.R
import com.jaycefr.jayto.playback.MediaControllerManager
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    mediaControllerManager: MediaControllerManager,
    onBack: () -> Unit
) {
    val state by mediaControllerManager.state.collectAsState()
    val song = state.currentSong ?: return
    val defaultArt = painterResource(id = R.drawable.ic_default_artwork)

    var showQueueSheet by remember { mutableStateOf(false) }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Queue (${state.queue.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (state.queue.isEmpty()) {
                    Text(
                        text = "No songs in queue",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(state.queue) { index, item ->
                            val isCurrent = index == state.currentMediaItemIndex
                            val title = item.mediaMetadata.title?.toString() ?: "Unknown"
                            val artist = item.mediaMetadata.artist?.toString() ?: "Unknown"

                            ListItem(
                                colors = ListItemDefaults.colors(
                                    containerColor = if (isCurrent) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                leadingContent = {
                                    AsyncImage(
                                        model = item.mediaMetadata.artworkUri ?: R.drawable.ic_default_artwork,
                                        placeholder = defaultArt,
                                        error = defaultArt,
                                        fallback = defaultArt,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(MaterialTheme.shapes.small),
                                        contentScale = ContentScale.Crop
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = title,
                                        maxLines = 1,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                },
                                supportingContent = {
                                    Text(text = artist, maxLines = 1)
                                },
                                trailingContent = {
                                    IconButton(onClick = { mediaControllerManager.removeFromQueue(index) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                                    }
                                },
                                modifier = Modifier.clickable {
                                    mediaControllerManager.seekToQueueIndex(index)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showQueueSheet = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Artwork Card
            Card(
                modifier = Modifier
                    .size(300.dp)
                    .aspectRatio(1f),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                AsyncImage(
                    model = song.mediaMetadata.artworkUri ?: R.drawable.ic_default_artwork,
                    placeholder = defaultArt,
                    error = defaultArt,
                    fallback = defaultArt,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = song.mediaMetadata.title?.toString() ?: "Unknown",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    animationMode = MarqueeAnimationMode.Immediately,
                    velocity = 40.dp
                )
            )
            Text(
                text = song.mediaMetadata.artist?.toString() ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    animationMode = MarqueeAnimationMode.Immediately,
                    velocity = 40.dp
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Slider
            Slider(
                value = state.currentPosition.toFloat(),
                onValueChange = { mediaControllerManager.seekTo(it.toLong()) },
                valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(state.currentPosition))
                Text(formatTime(state.duration))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { mediaControllerManager.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffleModeEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                IconButton(onClick = { mediaControllerManager.skipToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(48.dp))
                }
                FloatingActionButton(onClick = {
                    if (state.isPlaying) mediaControllerManager.pause() else mediaControllerManager.play()
                }) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { mediaControllerManager.skipToNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(48.dp))
                }
                IconButton(onClick = { mediaControllerManager.toggleRepeat() }) {
                    Icon(
                        imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
