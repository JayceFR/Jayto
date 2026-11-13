package com.jaycefr.jayto.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import com.jaycefr.jayto.MainActivity
import com.jaycefr.jayto.playback.MediaLibraryService

/**
 * PendingIntents for widget taps. Nothing here opens the app except tapping the cover/body.
 *
 * Two routes, chosen by whether the tap must be able to *start* playback from a dead process:
 *
 *  - Play / recent tiles → foreground-service start of [MediaLibraryService]. A widget tap is an
 *    allowed foreground-service start, and playback begins right away so Media3 posts its
 *    notification in time. Play is a media-button intent, so Media3 resumes the last queue itself.
 *
 *  - Pause / next / previous / shuffle / repeat → broadcast to [WidgetActionReceiver]. These only
 *    make sense with a live session; the receiver acts on the in-process player, or, if the process
 *    had died (widget stale), simply redraws the widget as stopped. Never a silent dead button,
 *    never a foreground start that could time out.
 */
internal object WidgetActions {
    const val ACTION_PAUSE = "com.jaycefr.jayto.widget.PAUSE"
    const val ACTION_NEXT = "com.jaycefr.jayto.widget.NEXT"
    const val ACTION_PREVIOUS = "com.jaycefr.jayto.widget.PREVIOUS"
    const val ACTION_TOGGLE_SHUFFLE = "com.jaycefr.jayto.widget.TOGGLE_SHUFFLE"
    const val ACTION_CYCLE_REPEAT = "com.jaycefr.jayto.widget.CYCLE_REPEAT"

    /** Service action: play a song from the "recently played" row. */
    const val ACTION_PLAY_RECENT = "com.jaycefr.jayto.widget.PLAY_RECENT"
    const val EXTRA_SONG_ID = "song_id"

    private const val REQUEST_OPEN_APP = 1
    private const val REQUEST_PLAY = 2
    private const val REQUEST_RECEIVER = 3

    fun openApp(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Explicit PLAY key (not PLAY_PAUSE) so a stale widget can never invert the state. */
    fun play(context: Context): PendingIntent = PendingIntent.getForegroundService(
        context,
        REQUEST_PLAY,
        Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(serviceComponent(context))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)),
        PendingIntent.FLAG_IMMUTABLE,
    )

    fun playRecent(context: Context, songId: Long): PendingIntent = PendingIntent.getForegroundService(
        context,
        REQUEST_PLAY,
        Intent(ACTION_PLAY_RECENT)
            .setComponent(serviceComponent(context))
            // Data makes each tile a distinct PendingIntent (extras don't count for identity).
            .setData(Uri.parse("jayto://widget/recent/$songId"))
            .putExtra(EXTRA_SONG_ID, songId),
        PendingIntent.FLAG_IMMUTABLE,
    )

    fun pause(context: Context) = receiver(context, ACTION_PAUSE)
    fun next(context: Context) = receiver(context, ACTION_NEXT)
    fun previous(context: Context) = receiver(context, ACTION_PREVIOUS)
    fun toggleShuffle(context: Context) = receiver(context, ACTION_TOGGLE_SHUFFLE)
    fun cycleRepeat(context: Context) = receiver(context, ACTION_CYCLE_REPEAT)

    private fun receiver(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_RECEIVER,
        Intent(action).setComponent(ComponentName(context, WidgetActionReceiver::class.java)),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceComponent(context: Context) = ComponentName(context, MediaLibraryService::class.java)
}
