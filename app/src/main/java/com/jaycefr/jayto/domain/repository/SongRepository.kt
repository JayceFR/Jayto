package com.jaycefr.jayto.domain.repository

import com.jaycefr.jayto.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun getAllSongs(): Flow<List<Song>>
    fun getRecentlyPlayed(): Flow<List<Song>>
    fun getMostPlayed(): Flow<List<Song>>
    fun getFavoriteSongs(): Flow<List<Song>>
    fun getAllAlbums(): Flow<List<String>>
    fun getSongsByAlbum(albumName: String): Flow<List<Song>>
    fun getAllArtists(): Flow<List<String>>
    fun getSongsByArtist(artistName: String): Flow<List<Song>>
    
    suspend fun getSongById(id: Long): Song?
    suspend fun scanLocalSongs()
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)
    suspend fun toggleHidden(id: Long, isHidden: Boolean)
    suspend fun reorderSongs(songIds: List<Long>)
    suspend fun incrementPlayCount(id: Long)
}
