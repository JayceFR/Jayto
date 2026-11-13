package com.jaycefr.jayto.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles widget controls that act on an existing session. Runs on the main thread, in the same
 * process as the player, so a live tap is a direct method call: no IPC, no service start.
 */
@AndroidEntryPoint
class WidgetActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var updater: NowPlayingWidgetUpdater

    override fun onReceive(context: Context, intent: Intent) {
        val player = updater.livePlayer
        if (player == null || player.mediaItemCount == 0) {
            // The process had died (or the queue is gone) while the widget still showed a live
            // session. Nothing to control: correct the widget so it offers Play/resume instead.
            updater.refresh(goAsync())
            return
        }
        when (intent.action) {
            WidgetActions.ACTION_PAUSE -> player.pause()
            WidgetActions.ACTION_NEXT -> player.seekToNext()
            WidgetActions.ACTION_PREVIOUS -> player.seekToPrevious()
            WidgetActions.ACTION_TOGGLE_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
            WidgetActions.ACTION_CYCLE_REPEAT -> player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
        // The player listener pushes the resulting state to the widget.
    }
}
