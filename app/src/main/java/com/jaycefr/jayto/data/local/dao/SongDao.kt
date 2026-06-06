package com.jaycefr.jayto.data.local.dao

import androidx.room.*
import com.jaycefr.jayto.data.local.entities.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE isHidden = 0 ORDER BY customOrder ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsList(): List<SongEntity>

    @Query("UPDATE songs SET customOrder = :newOrder WHERE id = :id")
    suspend fun updateSongOrder(id: Long, newOrder: Int)

    @Transaction
    suspend fun reorderSongs(songIds: List<Long>) {
        songIds.forEachIndexed { index, songId ->
            updateSongOrder(songId, index)
        }
    }

    @Query("SELECT * FROM songs WHERE id = :id AND isHidden = 0")
    suspend fun getSongById(id: Long): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Delete
    suspend fun deleteSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id NOT IN (:ids)")
    suspend fun deleteSongsNotInList(ids: List<Long>)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET isHidden = :isHidden WHERE id = :id")
    suspend fun updateHidden(id: Long, isHidden: Boolean)

    @Query("UPDATE songs SET artworkUri = :artworkUri WHERE id = :id")
    suspend fun updateArtworkUri(id: Long, artworkUri: String)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayed = :timestamp WHERE id = :id")
    suspend fun incrementPlayCount(id: Long, timestamp: Long)

    @Query("SELECT * FROM songs WHERE lastPlayed IS NOT NULL AND isHidden = 0 ORDER BY lastPlayed DESC LIMIT 50")
    fun getRecentlyPlayed(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isHidden = 0 ORDER BY playCount DESC LIMIT 50")
    fun getMostPlayed(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 AND isHidden = 0")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT DISTINCT album FROM songs WHERE isHidden = 0 ORDER BY album ASC")
    fun getAllAlbums(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE album = :albumName AND isHidden = 0 ORDER BY trackNumber ASC")
    fun getSongsByAlbum(albumName: String): Flow<List<SongEntity>>

    @Query("SELECT DISTINCT artist FROM songs WHERE isHidden = 0 ORDER BY artist ASC")
    fun getAllArtists(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE artist = :artistName AND isHidden = 0 ORDER BY album ASC, trackNumber ASC")
    fun getSongsByArtist(artistName: String): Flow<List<SongEntity>>
}
