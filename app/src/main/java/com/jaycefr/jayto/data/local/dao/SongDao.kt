package com.jaycefr.jayto.data.local.dao

import androidx.room.*
import com.jaycefr.jayto.data.local.entities.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE isHidden = 0 ORDER BY customOrder ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isHidden = 0 ORDER BY customOrder ASC, id ASC")
    fun getPagedSongs(): androidx.paging.PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs WHERE isHidden = 0 AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%') ORDER BY customOrder ASC, id ASC")
    fun searchPagedSongs(query: String): androidx.paging.PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs WHERE isHidden = 0 ORDER BY customOrder ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getSongsWindow(offset: Int, limit: Int): List<SongEntity>

    @Query("SELECT * FROM songs WHERE isHidden = 0 AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%') ORDER BY customOrder ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun searchSongsWindow(query: String, offset: Int, limit: Int): List<SongEntity>

    /** Position of a song in the (unhidden) list ordered by customOrder, id. Matches getSongsWindow offsets. */
    @Query("SELECT COUNT(*) FROM songs WHERE isHidden = 0 AND (customOrder < :order OR (customOrder = :order AND id < :id))")
    suspend fun getSongPosition(id: Long, order: Int): Int

    @Query("SELECT COUNT(*) FROM songs WHERE isHidden = 0 AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%') AND (customOrder < :order OR (customOrder = :order AND id < :id))")
    suspend fun searchSongPosition(query: String, id: Long, order: Int): Int

    @Query("SELECT customOrder FROM songs WHERE id = :id")
    suspend fun getCustomOrder(id: Long): Int?

    @Query("UPDATE songs SET customOrder = customOrder - 1 WHERE customOrder > :fromOrder AND customOrder <= :toOrder")
    suspend fun shiftOrdersDown(fromOrder: Int, toOrder: Int)

    @Query("UPDATE songs SET customOrder = customOrder + 1 WHERE customOrder >= :toOrder AND customOrder < :fromOrder")
    suspend fun shiftOrdersUp(fromOrder: Int, toOrder: Int)

    /** Moves one song into another song's slot, shifting everything in between. */
    @Transaction
    suspend fun moveSong(fromId: Long, toId: Long) {
        val fromOrder = getCustomOrder(fromId) ?: return
        val toOrder = getCustomOrder(toId) ?: return
        if (fromOrder == toOrder) return
        if (fromOrder < toOrder) shiftOrdersDown(fromOrder, toOrder) else shiftOrdersUp(fromOrder, toOrder)
        updateSongOrder(fromId, toOrder)
    }

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
