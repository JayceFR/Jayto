package com.jaycefr.jayto.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jaycefr.jayto.R
import com.jaycefr.jayto.playback.MediaControllerManager

@Composable
fun NowPlayingSheet(
    mediaControllerManager: MediaControllerManager,
    onClick: () -> Unit
) {
    val state by mediaControllerManager.state.collectAsState()
    val song = state.currentSong

    if (song != null) {
        val defaultArt = painterResource(id = R.drawable.ic_default_artwork)
        Surface(
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.mediaMetadata.artworkUri ?: R.drawable.ic_default_artwork,
                    placeholder = defaultArt,
                    error = defaultArt,
                    fallback = defaultArt,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = song.mediaMetadata.title?.toString() ?: "Unknown", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(text = song.mediaMetadata.artist?.toString() ?: "Unknown", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }

                Row {
                    IconButton(onClick = { mediaControllerManager.skipToPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                    }
                    IconButton(onClick = {
                        if (state.isPlaying) mediaControllerManager.pause() else mediaControllerManager.play()
                    }) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause"
                        )
                    }
                    IconButton(onClick = { mediaControllerManager.skipToNext() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next")
                    }
                }
            }
        }
    }
}
