package com.jaycefr.jayto.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.media3.common.Player
import com.jaycefr.jayto.R

/** Size classes. On Android 12+ the launcher picks one itself from [minSize]. */
internal enum class WidgetLayout(
    @LayoutRes val layoutRes: Int,
    val minSize: SizeF,
    val hasArtist: Boolean,
    val hasTransport: Boolean,
    val hasModes: Boolean,
    val hasRecents: Boolean,
    /** How prev/next are hidden without a session: GONE reflows, INVISIBLE keeps play centred. */
    val hiddenTransportVisibility: Int,
) {
    SMALL(R.layout.widget_now_playing_small, SizeF(100f, 80f), false, false, false, false, View.GONE),
    MEDIUM(R.layout.widget_now_playing_medium, SizeF(220f, 56f), true, true, false, false, View.GONE),
    LARGE(R.layout.widget_now_playing_large, SizeF(250f, 110f), true, true, true, false, View.INVISIBLE),
    /** Spotify-style: now playing + "recently played" row that plays without opening the app. */
    XL(R.layout.widget_now_playing_xl, SizeF(250f, 170f), true, true, true, true, View.INVISIBLE);

    companion object {
        /** Same rule the Android 12+ launcher applies: largest layout that fits, else the smallest. */
        fun forSize(widthDp: Float, heightDp: Float): WidgetLayout =
            entries
                .filter { it.minSize.width <= widthDp && it.minSize.height <= heightDp }
                .maxByOrNull { it.minSize.width * it.minSize.height }
                ?: SMALL

        /** Pre-31: portrait uses min width × max height. */
        fun forOptions(options: Bundle): WidgetLayout = forSize(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat(),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat(),
        )
    }
}

/** Turns a [WidgetState] into RemoteViews and pushes them to every placed widget. */
internal class NowPlayingWidgetRenderer(private val context: Context) {
    private val manager = AppWidgetManager.getInstance(context)
    private val component = ComponentName(context, NowPlayingWidgetProvider::class.java)
    private val artwork = WidgetArtworkLoader(context)

    suspend fun render(state: WidgetState, recents: List<RecentTile>) {
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) {
            // No widget placed: do no work and hold no bitmaps.
            artwork.clear()
            return
        }
        val art = artwork.load(state.artworkUri)
        val tiles = recents.map { it to artwork.loadTile(it.artworkUri) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // One parcel for all sizes; the launcher switches layouts on resize without calling us.
            // Nested views share a bitmap cache, so the cover is only sent once.
            val responsive = RemoteViews(WidgetLayout.entries.associate { it.minSize to build(it, state, art, tiles) })
            manager.updateAppWidget(ids, responsive)
        } else {
            val built = HashMap<WidgetLayout, RemoteViews>(WidgetLayout.entries.size)
            for (id in ids) {
                val layout = WidgetLayout.forOptions(manager.getAppWidgetOptions(id))
                manager.updateAppWidget(id, built.getOrPut(layout) { build(layout, state, art, tiles) })
            }
        }
    }

    /**
     * Android 15+ widget picker shows a real, generated preview. Rate-limited by the system, so the
     * caller only invokes this once per app version and retries later if it returns false.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun publishGeneratedPreview(): Boolean {
        val sample = WidgetState.EMPTY.copy(
            active = true,
            mediaId = "preview",
            title = context.getString(R.string.widget_preview_title),
            artist = context.getString(R.string.widget_preview_artist),
            hasPrevious = true,
            hasNext = true,
        )
        return manager.setWidgetPreview(
            component,
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
            build(WidgetLayout.XL, sample, art = null, tiles = emptyList()),
        )
    }

    private fun build(
        layout: WidgetLayout,
        state: WidgetState,
        art: WidgetArt?,
        tiles: List<Pair<RecentTile, Bitmap?>>,
    ): RemoteViews =
        RemoteViews(context.packageName, layout.layoutRes).apply {
            val palette = art?.palette
            setOnClickPendingIntent(android.R.id.background, WidgetActions.openApp(context))

            // Surface: cover-derived colour (or Material You / Jayto), plus the blurred cover where
            // the root can clip it to the rounded outline (Android 12+).
            tint(R.id.widget_bg_tint, palette?.background, R.color.widget_fallback_background)
            if (art != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setImageViewBitmap(R.id.widget_bg_art, art.ambient)
                setViewVisibility(R.id.widget_bg_art, View.VISIBLE)
            } else {
                setViewVisibility(R.id.widget_bg_art, View.GONE)
            }

            if (art != null) {
                setImageViewBitmap(R.id.widget_art, art.cover)
            } else {
                setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
            }

            setTextViewText(
                R.id.widget_title,
                state.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.widget_nothing_playing),
            )
            if (layout.hasArtist) setTextViewText(R.id.widget_artist, subtitle(state))

            bindPlayButton(state, palette)
            if (layout.hasTransport) bindTransport(layout, state)
            if (layout.hasModes) bindModes(state, palette)
            if (layout.hasRecents) bindRecents(tiles)
        }

    private fun RemoteViews.bindPlayButton(state: WidgetState, palette: WidgetPalette?) {
        val showPause = state.active && state.playWhenReady
        setImageViewResource(R.id.widget_play, if (showPause) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        setContentDescription(
            R.id.widget_play_button,
            context.getString(if (showPause) R.string.widget_pause else R.string.widget_play),
        )
        tint(R.id.widget_play_bg, palette?.accent, R.color.widget_fallback_accent)
        tint(R.id.widget_play, palette?.onAccent, R.color.widget_fallback_on_accent)
        setOnClickPendingIntent(
            R.id.widget_play_button,
            when {
                showPause -> WidgetActions.pause(context)
                // Media3 resumes the last queue (onPlaybackResumption) if the session is empty.
                state.active || state.hasMedia -> WidgetActions.play(context)
                // Never played anything: resumption could find nothing and the foreground start
                // would time out. Open the app instead.
                else -> WidgetActions.openApp(context)
            },
        )
    }

    private fun RemoteViews.bindTransport(layout: WidgetLayout, state: WidgetState) {
        val visibility = if (state.active && state.hasMedia) View.VISIBLE else layout.hiddenTransportVisibility
        setViewVisibility(R.id.widget_prev, visibility)
        setViewVisibility(R.id.widget_next, visibility)
        if (visibility != View.VISIBLE) return

        setOnClickPendingIntent(R.id.widget_prev, WidgetActions.previous(context))
        setOnClickPendingIntent(R.id.widget_next, WidgetActions.next(context))
        setInt(R.id.widget_next, "setImageAlpha", if (state.hasNext) ALPHA_ENABLED else ALPHA_DISABLED)
    }

    private fun RemoteViews.bindModes(state: WidgetState, palette: WidgetPalette?) {
        val visibility = if (state.active && state.hasMedia) View.VISIBLE else View.INVISIBLE
        setViewVisibility(R.id.widget_shuffle, visibility)
        setViewVisibility(R.id.widget_repeat, visibility)
        if (visibility != View.VISIBLE) return

        setOnClickPendingIntent(R.id.widget_shuffle, WidgetActions.toggleShuffle(context))
        setContentDescription(
            R.id.widget_shuffle,
            context.getString(if (state.shuffle) R.string.widget_shuffle_on else R.string.widget_shuffle_off),
        )
        modeTint(R.id.widget_shuffle, state.shuffle, palette)

        setOnClickPendingIntent(R.id.widget_repeat, WidgetActions.cycleRepeat(context))
        val repeatOn = state.repeatMode != Player.REPEAT_MODE_OFF
        setImageViewResource(
            R.id.widget_repeat,
            if (state.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_widget_repeat_one else R.drawable.ic_widget_repeat,
        )
        setContentDescription(
            R.id.widget_repeat,
            context.getString(
                when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.string.widget_repeat_one
                    Player.REPEAT_MODE_ALL -> R.string.widget_repeat_all
                    else -> R.string.widget_repeat_off
                },
            ),
        )
        modeTint(R.id.widget_repeat, repeatOn, palette)
    }

    /**
     * Each tile starts its song (and the recents after it) straight from the widget: a
     * foreground-service start, no activity. Unused slots stay INVISIBLE so the row keeps its rhythm.
     */
    private fun RemoteViews.bindRecents(tiles: List<Pair<RecentTile, Bitmap?>>) {
        RECENT_TILE_IDS.forEachIndexed { index, viewId ->
            val entry = tiles.getOrNull(index)
            if (entry == null) {
                setViewVisibility(viewId, View.INVISIBLE)
                return@forEachIndexed
            }
            val (tile, bitmap) = entry
            setViewVisibility(viewId, View.VISIBLE)
            if (bitmap != null) {
                setImageViewBitmap(viewId, bitmap)
            } else {
                setImageViewResource(viewId, R.drawable.widget_art_placeholder)
            }
            setContentDescription(viewId, context.getString(R.string.widget_play_recent, tile.title))
            setOnClickPendingIntent(viewId, WidgetActions.playRecent(context, tile.songId))
        }
    }

    private fun subtitle(state: WidgetState): String = when {
        !state.hasMedia -> context.getString(R.string.widget_tap_to_start)
        !state.active && state.artist.isNullOrBlank() -> context.getString(R.string.widget_tap_to_resume)
        else -> state.artist.orEmpty()
    }

    private fun RemoteViews.modeTint(id: Int, on: Boolean, palette: WidgetPalette?) {
        if (on) tint(id, palette?.accent, R.color.widget_fallback_accent) else tint(id, null, R.color.widget_text_secondary)
    }

    /**
     * Android 12+: image tint lists. A colour *resource* is resolved by the launcher, so the
     * Material You fallback follows wallpaper changes without a re-render.
     * Older: colour filters (overrides the XML tint).
     */
    private fun RemoteViews.tint(id: Int, @ColorInt color: Int?, @ColorRes fallback: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (color != null) {
                setColorStateList(id, "setImageTintList", ColorStateList.valueOf(color))
            } else {
                setColorStateList(id, "setImageTintList", fallback)
            }
        } else {
            setInt(id, "setColorFilter", color ?: context.getColor(fallback))
        }
    }

    private companion object {
        const val ALPHA_ENABLED = 255
        const val ALPHA_DISABLED = 97

        val RECENT_TILE_IDS = intArrayOf(
            R.id.widget_recent_0,
            R.id.widget_recent_1,
            R.id.widget_recent_2,
            R.id.widget_recent_3,
            R.id.widget_recent_4,
        )
    }
}
