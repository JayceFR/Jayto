package com.jaycefr.jayto.domain.model

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

fun Song.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artworkUri?.let { Uri.parse(it) })
        .setTrackNumber(trackNumber)
        .setDiscNumber(discNumber)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .build()

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.parse(fileUri))
        .setMediaMetadata(metadata)
        .build()
}
