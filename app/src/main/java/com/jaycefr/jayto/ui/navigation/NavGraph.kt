package com.jaycefr.jayto.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jaycefr.jayto.playback.MediaControllerManager
import com.jaycefr.jayto.ui.screens.*

@Composable
fun NavGraph(
    navController: NavHostController,
    mediaControllerManager: MediaControllerManager
) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = hiltViewModel(),
                songsViewModel = hiltViewModel(),
                onNavigateToSongs = { navController.navigate("songs") },
                onNavigateToAlbums = { navController.navigate("albums") },
                onNavigateToArtists = { navController.navigate("artists") },
                onNavigateToPlaylists = { navController.navigate("playlists") }
            )
        }
        composable("songs") {
            SongsScreen(viewModel = hiltViewModel())
        }
        composable("albums") {
            AlbumsScreen(
                viewModel = hiltViewModel(),
                onAlbumClick = { navController.navigate("album_detail/$it") }
            )
        }
        composable(
            route = "album_detail/{albumName}",
            arguments = listOf(navArgument("albumName") { type = NavType.StringType })
        ) { backStackEntry ->
            val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
            AlbumDetailScreen(viewModel = hiltViewModel(), albumName = albumName)
        }
        composable("artists") {
            ArtistsScreen(
                viewModel = hiltViewModel(),
                onArtistClick = { navController.navigate("artist_detail/$it") }
            )
        }
        composable(
            route = "artist_detail/{artistName}",
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
            ArtistDetailScreen(viewModel = hiltViewModel(), artistName = artistName)
        }
        composable("playlists") {
            PlaylistsScreen(
                viewModel = hiltViewModel(),
                onPlaylistClick = { navController.navigate("playlist_detail/${it.id}/${it.name}") }
            )
        }
        composable(
            route = "playlist_detail/{playlistId}/{playlistName}",
            arguments = listOf(
                navArgument("playlistId") { type = NavType.LongType },
                navArgument("playlistName") { type = NavType.StringType }
            )
        ) {
            PlaylistDetailScreen(viewModel = hiltViewModel())
        }
        composable("now_playing") {
            NowPlayingScreen(
                mediaControllerManager = mediaControllerManager,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
