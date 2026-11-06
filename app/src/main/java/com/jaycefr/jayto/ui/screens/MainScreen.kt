package com.jaycefr.jayto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jaycefr.jayto.playback.MediaControllerManager
import com.jaycefr.jayto.ui.navigation.NavGraph

@Composable
fun MainScreen(
    mediaControllerManager: MediaControllerManager,
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            NowPlayingSheet(
                mediaControllerManager = mediaControllerManager,
                onClick = { navController.navigate("now_playing") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavGraph(
                navController = navController,
                mediaControllerManager = mediaControllerManager
            )
        }
    }
}
