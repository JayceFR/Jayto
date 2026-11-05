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
                songsViewModel = hiltViewModel(),
                playlistsViewModel = hiltViewModel(),
                albumsViewModel = hiltViewModel(),
                artistsViewModel = hiltViewModel(),
                onAlbumClick = { navController.navigate("album_detail/$it") },
                onArtistClick = { navController.navigate("artist_detail/$it") },
                onPlaylistClick = { navController.navigate("playlist_detail/${it.id}/${it.name}") }
            )
        }
        composable(
            route = "album_detail/{albumName}",
            arguments = listOf(navArgument("albumName") { type = NavType.StringType })
        ) { backStackEntry ->
            val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
            AlbumDetailScreen(viewModel = hiltViewModel(), albumName = albumName)
        }
        composable(
            route = "artist_detail/{artistName}",
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
            ArtistDetailScreen(viewModel = hiltViewModel(), artistName = artistName)
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
