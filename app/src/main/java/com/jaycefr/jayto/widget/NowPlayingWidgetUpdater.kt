package com.jaycefr.jayto.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.content.pm.PackageInfoCompat
import androidx.media3.common.Player
import com.jaycefr.jayto.domain.repository.SongRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the home-screen widget in sync with the player.
 *
 * Push-based: the playback service [attach]es its player; every player event is reduced to a
 * [WidgetState] and only *visible* changes are rendered. Renders are conflated (a newer state
 * cancels an in-flight one), run off the main thread, and are skipped entirely when no widget is
 * placed.
 */
@Singleton
class NowPlayingWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songRepository: SongRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val renderer = NowPlayingWidgetRenderer(context)
    private val snapshots = WidgetSnapshotStore(context)
    private val states = MutableStateFlow<WidgetState?>(null)

    /** Main thread only. */
    private var player: Player? = null

    /** The session's player if one is alive in this process. Main thread only. */
    val livePlayer: Player? get() = player

    /**
     * "Recently played" tiles. Room re-emits on every songs-table write (play counts, art), so
     * dedupe on what's actually shown.
     */
    private val recentTiles: Flow<List<RecentTile>> = songRepository.getRecentlyPlayed()
        .map { songs -> songs.take(MAX_TILES).map { RecentTile(it.id, it.title, it.artworkUri) } }
        .distinctUntilChanged()
        .catch { emit(emptyList()) }

    @Volatile
    private var lastTiles: List<RecentTile> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    init {
        scope.launch {
            combine(states.filterNotNull(), recentTiles) { state, tiles -> state to tiles }
                .collectLatest { (state, tiles) ->
                lastTiles = tiles
                try {
                    renderer.render(state, tiles)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A widget must never take the player down with it.
                    Log.w(TAG, "Widget render failed", e)
                }
            }
        }
        scope.launch { maybePublishGeneratedPreview() }
    }

    @MainThread
    fun attach(player: Player) {
        if (this.player === player) return
        detach()
        this.player = player
        player.addListener(listener)
        publish(player)
    }

    /**
     * Session is going away: keep showing the song, but as resumable rather than live.
     *
     * @param renderNow push synchronously (bounded, uses cached art). Use when the process may be
     * killed right after, e.g. task removal on aggressive OEMs, otherwise the widget would keep
     * showing a session that no longer exists.
     */
    @MainThread
    fun detach(renderNow: Boolean = false) {
        val current = player ?: return
        current.removeListener(listener)
        player = null
        val last = states.value?.takeIf { it.hasMedia } ?: snapshots.read()
        val inactive = last.asInactive()
        states.value = inactive
        if (renderNow) {
            try {
                runBlocking {
                    withTimeoutOrNull(DETACH_RENDER_TIMEOUT_MS) { renderer.render(inactive, lastTiles) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Widget detach render failed", e)
            }
        }
    }

    /**
     * Re-render now, regardless of whether state changed (the launcher asked: widget added,
     * reboot, app update, resize on old Android). Called from the provider inside goAsync().
     */
    fun refresh(pendingResult: BroadcastReceiver.PendingResult) {
        scope.launch {
            try {
                withTimeoutOrNull(RENDER_TIMEOUT_MS) {
                    // No live session in this process -> fall back to the persisted last song.
                    val state = states.value ?: snapshots.read().asInactive()
                    val tiles = runCatching { recentTiles.first() }.getOrDefault(lastTiles)
                    renderer.render(state, tiles)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Widget refresh failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    @MainThread
    private fun publish(player: Player) {
        val previous = states.value
        val item = player.currentMediaItem
        val metadata = player.mediaMetadata
        val state = WidgetState(
            active = true,
            mediaId = item?.mediaId,
            title = metadata.title?.toString(),
            artist = metadata.artist?.toString(),
            artworkUri = metadata.artworkUri?.toString(),
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
            hasPrevious = player.hasPreviousMediaItem(),
            hasNext = player.hasNextMediaItem(),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        if (state == previous) return
        states.value = state

        if (previous == null || previous.mediaId != state.mediaId || previous.title != state.title ||
            previous.artworkUri != state.artworkUri || previous.playWhenReady != state.playWhenReady
        ) {
            snapshots.write(state)
        }
    }

    private fun maybePublishGeneratedPreview() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        try {
            val version = PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0),
            )
            if (snapshots.previewVersion() == version) return
            if (renderer.publishGeneratedPreview()) snapshots.setPreviewVersion(version)
        } catch (e: Exception) {
            // Rate-limited or unsupported launcher: the static previewLayout still applies.
            Log.d(TAG, "Generated preview not published", e)
        }
    }

    private companion object {
        const val TAG = "NowPlayingWidget"
        /** goAsync() allows ~10s; leave headroom. */
        const val RENDER_TIMEOUT_MS = 8_000L
        const val DETACH_RENDER_TIMEOUT_MS = 500L
        const val MAX_TILES = 5
    }
}
