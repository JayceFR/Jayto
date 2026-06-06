package com.jaycefr.jayto.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,
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
    val playCount: Int = 0,
    val lastPlayed: Long? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val customOrder: Int = 0
)
