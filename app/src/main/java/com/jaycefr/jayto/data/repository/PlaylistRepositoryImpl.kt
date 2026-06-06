package com.jaycefr.jayto.data.repository

import com.jaycefr.jayto.data.local.dao.PlaylistDao
import com.jaycefr.jayto.data.local.entities.PlaylistEntity
import com.jaycefr.jayto.data.local.entities.PlaylistSongCrossRef
import com.jaycefr.jayto.data.local.entities.toDomain
import com.jaycefr.jayto.domain.model.Song
import com.jaycefr.jayto.domain.repository.Playlist
import com.jaycefr.jayto.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao
) : PlaylistRepository {

    override fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAllPlaylists().map { entities ->
            entities.map { Playlist(it.id, it.name, it.createdAt, it.updatedAt) }
        }

    override fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> =
        playlistDao.getSongsForPlaylist(playlistId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name))

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(PlaylistEntity(id = playlistId, name = ""))
    }

    override suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val maxPos = playlistDao.getMaxPosition(playlistId) ?: -1
        playlistDao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId, songId, maxPos + 1))
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(PlaylistSongCrossRef(playlistId, songId, 0))
    }

    override suspend fun reorderPlaylist(playlistId: Long, songIds: List<Long>) {
        playlistDao.reorderPlaylist(playlistId, songIds)
    }
}
