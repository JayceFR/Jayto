package com.jaycefr.jayto.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jaycefr.jayto.data.local.dao.PlaylistDao
import com.jaycefr.jayto.data.local.dao.SongDao
import com.jaycefr.jayto.data.local.entities.PlaylistEntity
import com.jaycefr.jayto.data.local.entities.PlaylistSongCrossRef
import com.jaycefr.jayto.data.local.entities.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JaytoDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val DATABASE_NAME = "jayto_db"
    }
}
