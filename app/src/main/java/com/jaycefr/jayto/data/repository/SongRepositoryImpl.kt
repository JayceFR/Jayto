package com.jaycefr.jayto.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.jaycefr.jayto.data.local.dao.SongDao
import com.jaycefr.jayto.data.local.entities.SongEntity
import com.jaycefr.jayto.data.local.entities.toDomain
import com.jaycefr.jayto.domain.model.Song
import com.jaycefr.jayto.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SongRepositoryImpl @Inject constructor(
    private val context: Context,
    private val songDao: SongDao
) : SongRepository {

    override fun getAllSongs(): Flow<List<Song>> =
        songDao.getAllSongs().map { entities -> entities.map { it.toDomain() } }

    override fun getRecentlyPlayed(): Flow<List<Song>> =
        songDao.getRecentlyPlayed().map { entities -> entities.map { it.toDomain() } }

    override fun getMostPlayed(): Flow<List<Song>> =
        songDao.getMostPlayed().map { entities -> entities.map { it.toDomain() } }

    override fun getFavoriteSongs(): Flow<List<Song>> =
        songDao.getFavoriteSongs().map { entities -> entities.map { it.toDomain() } }

    override fun getAllAlbums(): Flow<List<String>> = songDao.getAllAlbums()

    override fun getSongsByAlbum(albumName: String): Flow<List<Song>> =
        songDao.getSongsByAlbum(albumName).map { entities -> entities.map { it.toDomain() } }

    override fun getAllArtists(): Flow<List<String>> = songDao.getAllArtists()

    override fun getSongsByArtist(artistName: String): Flow<List<Song>> =
        songDao.getSongsByArtist(artistName).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getSongById(id: Long): Song? =
        songDao.getSongById(id)?.toDomain()

    override suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        songDao.updateFavorite(id, isFavorite)
    }

    override suspend fun toggleHidden(id: Long, isHidden: Boolean) {
        songDao.updateHidden(id, isHidden)
    }

    override suspend fun reorderSongs(songIds: List<Long>) {
        songDao.reorderSongs(songIds)
    }

    override suspend fun incrementPlayCount(id: Long) {
        songDao.incrementPlayCount(id, System.currentTimeMillis())
    }

    override suspend fun scanLocalSongs() = withContext(Dispatchers.IO) {
        val currentSongsMap = songDao.getAllSongsList().associateBy { it.id }
        var maxOrder = currentSongsMap.values.maxOfOrNull { it.customOrder } ?: -1
        
        val songsToInsert = mutableListOf<SongEntity>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val fileUri = cursor.getString(dataColumn)
                
                // Skip if file doesn't exist
                if (!java.io.File(fileUri).exists()) continue

                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn) ?: "<Unknown>"
                val album = cursor.getString(albumColumn) ?: "<Unknown>"
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val trackNumber = cursor.getInt(trackColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)

                val artworkUri = ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/albumart"),
                    albumId
                ).toString()

                val existingSong = currentSongsMap[id]
                val isHidden = existingSong?.isHidden ?: false
                val isFavorite = existingSong?.isFavorite ?: false
                val playCount = existingSong?.playCount ?: 0
                val lastPlayed = existingSong?.lastPlayed
                val customOrder = existingSong?.customOrder ?: ++maxOrder

                songsToInsert.add(
                    SongEntity(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        fileUri = fileUri,
                        artworkUri = artworkUri,
                        trackNumber = trackNumber % 1000,
                        discNumber = trackNumber / 1000,
                        dateAdded = dateAdded,
                        dateModified = dateModified,
                        customOrder = customOrder,
                        isHidden = isHidden,
                        isFavorite = isFavorite,
                        playCount = playCount,
                        lastPlayed = lastPlayed
                    )
                )
            }
        }

        if (songsToInsert.isNotEmpty()) {
            songDao.insertSongs(songsToInsert)
            songDao.deleteSongsNotInList(songsToInsert.map { it.id })
        }
    }
}
