package com.jaycefr.jayto.domain.repository

import com.jaycefr.jayto.domain.model.Song
import kotlinx.coroutines.flow.Flow

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<Playlist>>
    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>>
    suspend fun createPlaylist(name: String): Long
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)
}
