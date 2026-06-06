package com.jaycefr.jayto.domain.model

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val fileUri: String,
    val artworkUri: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val dateAdded: Long,
    val dateModified: Long,
    val playCount: Int,
    val lastPlayed: Long?,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    val customOrder: Int
)
