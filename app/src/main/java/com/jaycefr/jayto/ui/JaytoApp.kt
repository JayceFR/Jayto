package com.jaycefr.jayto.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

@Composable
fun JaytoApp() {

    val context = LocalContext.current

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    fun isGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    var hasPermission by remember { mutableStateOf(isGranted()) }
    var showRationale by remember { mutableStateOf(!hasPermission) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        showRationale = !granted
    }

    when {

        hasPermission -> {
            HomeScreen()
        }

        showRationale -> {
            PermissionScreen(
                onAllow = {
                    launcher.launch(permission)
                },
                onDeny = {
                    showRationale = false
                }
            )
        }

        else -> {
            PermissionDeniedScreen(
                onRetry = {
                    showRationale = true
                },
                onExit = {
                    // finish activity if needed
                }
            )
        }
    }
}