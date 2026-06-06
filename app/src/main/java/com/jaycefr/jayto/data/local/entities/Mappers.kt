package com.jaycefr.jayto.data.local.entities

import com.jaycefr.jayto.domain.model.Song

fun SongEntity.toDomain() = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    fileUri = fileUri,
    artworkUri = artworkUri,
    trackNumber = trackNumber,
    discNumber = discNumber,
    dateAdded = dateAdded,
    dateModified = dateModified,
    playCount = playCount,
    lastPlayed = lastPlayed,
    isFavorite = isFavorite
)

fun Song.toEntity() = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    fileUri = fileUri,
    artworkUri = artworkUri,
    trackNumber = trackNumber,
    discNumber = discNumber,
    dateAdded = dateAdded,
    dateModified = dateModified,
    playCount = playCount,
    lastPlayed = lastPlayed,
    isFavorite = isFavorite
)
