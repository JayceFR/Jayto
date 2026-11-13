package com.jaycefr.jayto.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Colours derived from a cover. All ints are opaque ARGB. */
data class WidgetPalette(
    @ColorInt val background: Int,
    @ColorInt val accent: Int,
    @ColorInt val onAccent: Int,
) {
    companion object {
        private const val TINY = 10

        /**
         * Picks the most vivid colour of a downscaled cover and derives a dark surface, a bright
         * accent for the play button and a readable icon colour on that accent. Greyscale covers
         * get a neutral palette instead of a muddy tint.
         */
        fun from(tiny: Bitmap): WidgetPalette {
            val hsv = FloatArray(3)
            var bestScore = -1f
            var bestHue = 0f
            var bestSat = 0f
            for (y in 0 until tiny.height) {
                for (x in 0 until tiny.width) {
                    Color.colorToHSV(tiny.getPixel(x, y), hsv)
                    // Ignore near-black and blown-out pixels; prefer saturated, mid-bright ones.
                    if (hsv[2] < 0.18f || hsv[2] > 0.98f) continue
                    val score = hsv[1] * (0.6f + 0.4f * hsv[2])
                    if (score > bestScore) {
                        bestScore = score
                        bestHue = hsv[0]
                        bestSat = hsv[1]
                    }
                }
            }
            if (bestScore < 0f || bestSat < 0.12f) {
                return WidgetPalette(
                    background = 0xFF17171B.toInt(),
                    accent = 0xFFE9E7EE.toInt(),
                    onAccent = 0xFF17171B.toInt(),
                )
            }
            return WidgetPalette(
                background = Color.HSVToColor(floatArrayOf(bestHue, (bestSat * 0.7f).coerceIn(0.25f, 0.55f), 0.17f)),
                accent = Color.HSVToColor(floatArrayOf(bestHue, bestSat.coerceIn(0.35f, 0.7f), 0.93f)),
                onAccent = Color.HSVToColor(floatArrayOf(bestHue, 0.6f, 0.15f)),
            )
        }

        internal const val TINY_SIZE = TINY
    }
}

/** Processed artwork for one song: a rounded cover, a blurred ambient backdrop and a palette. */
class WidgetArt(val cover: Bitmap, val ambient: Bitmap, val palette: WidgetPalette)

/**
 * Loads covers through the app's Coil [coil.ImageLoader] (shares its memory/disk cache) and keeps
 * the last processed result, since the widget re-renders the same song on every play/pause.
 */
internal class WidgetArtworkLoader(private val context: Context) {
    private val mutex = Mutex()
    private var cachedUri: String? = null
    private var cachedArt: WidgetArt? = null

    suspend fun load(uri: String?): WidgetArt? {
        if (uri.isNullOrBlank()) return null
        return mutex.withLock {
            if (uri == cachedUri) return@withLock cachedArt
            val art = decode(uri)
            // Cache misses too, so a song without a cover doesn't retry the decode every event.
            cachedUri = uri
            cachedArt = art
            art
        }
    }

    /** Small rounded cover for a "recently played" tile. Null when the song has no artwork. */
    suspend fun loadTile(uri: String?): Bitmap? {
        if (uri.isNullOrBlank()) return null
        synchronized(tiles) { tiles.get(uri) }?.let { return it.bitmap }
        val bitmap = fetch(uri, TILE_PX)?.let { drawable ->
            withContext(Dispatchers.Default) {
                roundedSquare(drawable.toBitmap(), TILE_PX, TILE_PX * CORNER_FRACTION)
            }
        }
        synchronized(tiles) { tiles.put(uri, TileEntry(bitmap)) }
        return bitmap
    }

    fun clear() {
        cachedUri = null
        cachedArt = null
        synchronized(tiles) { tiles.evictAll() }
    }

    /** Wrapper so "no artwork" is cacheable too (LruCache rejects null values). */
    private class TileEntry(val bitmap: Bitmap?)

    /** A couple more than the row shows, so a shift by one song re-decodes nothing. */
    private val tiles = LruCache<String, TileEntry>(TILE_CACHE_SIZE)

    private suspend fun fetch(uri: String, sizePx: Int) =
        (context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(uri)
                .size(sizePx)
                .scale(Scale.FILL)
                // RemoteViews must parcel the pixels; hardware bitmaps can't be read back.
                .allowHardware(false)
                .build(),
        ) as? SuccessResult)?.drawable

    private suspend fun decode(uri: String): WidgetArt? {
        val drawable = fetch(uri, COVER_PX) ?: return null

        return withContext(Dispatchers.Default) {
            // May be Coil's cached bitmap: read-only from here on.
            val source = drawable.toBitmap()
            val tiny = Bitmap.createScaledBitmap(source, WidgetPalette.TINY_SIZE, WidgetPalette.TINY_SIZE, true)
            WidgetArt(
                cover = roundedSquare(source, COVER_PX, COVER_PX * CORNER_FRACTION),
                // Bilinear upscale of a 10px image: a smooth colour field, i.e. a very cheap blur.
                ambient = Bitmap.createScaledBitmap(tiny, AMBIENT_PX, AMBIENT_PX, true),
                palette = WidgetPalette.from(tiny),
            )
        }
    }

    /** Centre-crops to a square and rounds the corners in a single draw. */
    private fun roundedSquare(source: Bitmap, size: Int, radius: Float): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val scale = max(size.toFloat() / source.width, size.toFloat() / source.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((size - source.width * scale) / 2f, (size - source.height * scale) / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(matrix)
            }
        }
        Canvas(output).drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, paint)
        return output
    }

    private companion object {
        /** Sharp at the largest size the cover is drawn (~168dp) on typical densities. */
        const val COVER_PX = 384
        const val AMBIENT_PX = 96
        /** ~64dp tiles at xxhdpi. */
        const val TILE_PX = 176
        const val TILE_CACHE_SIZE = 8
        /** Radius relative to the cover, so it looks the same at every widget size. */
        const val CORNER_FRACTION = 0.1f
    }
}
