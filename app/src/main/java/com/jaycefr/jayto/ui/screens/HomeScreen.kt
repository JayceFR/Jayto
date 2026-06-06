package com.jaycefr.jayto.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jaycefr.jayto.domain.model.Song
import com.jaycefr.jayto.ui.viewmodel.HomeViewModel
import com.jaycefr.jayto.ui.viewmodel.SongsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    songsViewModel: SongsViewModel,
    onNavigateToSongs: () -> Unit,
    onNavigateToAlbums: () -> Unit,
    onNavigateToArtists: () -> Unit,
    onNavigateToPlaylists: () -> Unit
) {
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Jayto Music") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            if (recentlyPlayed.isNotEmpty()) {
                item { Text("Recently Played", style = MaterialTheme.typography.titleLarge) }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(recentlyPlayed.withIndex().toList()) { (index, song) ->
                            SongCard(song = song, onClick = { songsViewModel.playSongs(recentlyPlayed, index) })
                        }
                    }
                }
            }

            if (favorites.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item { Text("Favorites", style = MaterialTheme.typography.titleLarge) }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(favorites.withIndex().toList()) { (index, song) ->
                            SongCard(song = song, onClick = { songsViewModel.playSongs(favorites, index) })
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { Text("Library", style = MaterialTheme.typography.titleLarge) }
            item { HomeCategoryItem("All Songs", onNavigateToSongs) }
            item { HomeCategoryItem("Albums", onNavigateToAlbums) }
            item { HomeCategoryItem("Artists", onNavigateToArtists) }
            item { HomeCategoryItem("Playlists", onNavigateToPlaylists) }
        }
    }
}

@Composable
fun SongCard(song: Song, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = song.artworkUri,
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

@Composable
fun HomeCategoryItem(title: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
