package com.jaycefr.jayto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jaycefr.jayto.playback.MediaControllerManager
import com.jaycefr.jayto.ui.PermissionDeniedScreen
import com.jaycefr.jayto.ui.PermissionScreen
import com.jaycefr.jayto.ui.screens.MainScreen
import com.jaycefr.jayto.ui.theme.JaytoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var mediaControllerManager: MediaControllerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JaytoTheme {
                val context = LocalContext.current
                val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }

                fun hasAudioPermission(): Boolean {
                    val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    return ContextCompat.checkSelfPermission(context, audioPerm) == PackageManager.PERMISSION_GRANTED
                }

                var isPermissionGranted by remember { mutableStateOf(hasAudioPermission()) }
                var showDeniedScreen by remember { mutableStateOf(false) }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    val granted = hasAudioPermission()
                    isPermissionGranted = granted
                    showDeniedScreen = !granted
                }

                LaunchedEffect(Unit) {
                    if (!isPermissionGranted) {
                        launcher.launch(requiredPermissions)
                    }
                }

                if (isPermissionGranted) {
                    MainScreen(mediaControllerManager = mediaControllerManager)
                } else if (showDeniedScreen) {
                    PermissionDeniedScreen(
                        onRetry = { launcher.launch(requiredPermissions) },
                        onExit = { finish() }
                    )
                } else {
                    PermissionScreen(
                        onAllow = { launcher.launch(requiredPermissions) },
                        onDeny = { showDeniedScreen = true }
                    )
                }
            }
        }
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val uri = intent.data
        if ((action == Intent.ACTION_VIEW || action == "android.intent.action.MUSIC_PLAYER") && uri != null) {
            mediaControllerManager.playMediaUri(uri)
        }
    }
}
