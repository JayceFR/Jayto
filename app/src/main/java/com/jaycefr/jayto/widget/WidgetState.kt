package com.jaycefr.jayto.widget

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.Player

/**
 * Everything the widget shows, and nothing else: equality decides whether a re-render happens, so
 * player events that don't change anything visible (position ticks, buffering, seeks) cost nothing.
 *
 * Deliberately no playback clock: a launcher-side timer can't be stopped if the process is killed
 * mid-song, and would then run past the end of the track. Same choice as Spotify's widget.
 */
data class WidgetState(
    /** A live media session exists in this process, so transport intents will land. */
    val active: Boolean,
    val mediaId: String?,
    val title: String?,
    val artist: String?,
    val artworkUri: String?,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val shuffle: Boolean,
    val repeatMode: Int,
) {
    val hasMedia: Boolean get() = mediaId != null

    /** Same song shown as paused, for when the session goes away. */
    fun asInactive(): WidgetState = copy(
        active = false,
        playWhenReady = false,
        isPlaying = false,
    )

    companion object {
        val EMPTY = WidgetState(
            active = false,
            mediaId = null,
            title = null,
            artist = null,
            artworkUri = null,
            playWhenReady = false,
            isPlaying = false,
            hasPrevious = false,
            hasNext = false,
            shuffle = false,
            repeatMode = Player.REPEAT_MODE_OFF,
        )
    }
}

/**
 * Last song shown, persisted so the widget can still render it after the process dies (reboot,
 * app update, launcher asking for a refresh). Small and written only when the song or play state
 * changes.
 */
internal class WidgetSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("now_playing_widget", Context.MODE_PRIVATE)

    fun read(): WidgetState {
        val mediaId = prefs.getString(KEY_MEDIA_ID, null) ?: return WidgetState.EMPTY
        return WidgetState.EMPTY.copy(
            mediaId = mediaId,
            title = prefs.getString(KEY_TITLE, null),
            artist = prefs.getString(KEY_ARTIST, null),
            artworkUri = prefs.getString(KEY_ARTWORK, null),
        )
    }

    fun write(state: WidgetState) {
        if (!state.hasMedia) return
        prefs.edit {
            putString(KEY_MEDIA_ID, state.mediaId)
            putString(KEY_TITLE, state.title)
            putString(KEY_ARTIST, state.artist)
            putString(KEY_ARTWORK, state.artworkUri)
        }
    }

    fun previewVersion(): Long = prefs.getLong(KEY_PREVIEW_VERSION, -1L)

    fun setPreviewVersion(version: Long) = prefs.edit { putLong(KEY_PREVIEW_VERSION, version) }

    private companion object {
        const val KEY_MEDIA_ID = "media_id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ARTWORK = "artwork"
        const val KEY_PREVIEW_VERSION = "preview_version"
    }
}

/** A song in the widget's "recently played" row. */
data class RecentTile(val songId: Long, val title: String, val artworkUri: String?)
