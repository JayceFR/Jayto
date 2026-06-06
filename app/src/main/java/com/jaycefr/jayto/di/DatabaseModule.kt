package com.jaycefr.jayto.di

import android.content.Context
import androidx.room.Room
import com.jaycefr.jayto.data.local.JaytoDatabase
import com.jaycefr.jayto.data.local.dao.PlaylistDao
import com.jaycefr.jayto.data.local.dao.SongDao
import com.jaycefr.jayto.data.repository.PlaylistRepositoryImpl
import com.jaycefr.jayto.data.repository.SongRepositoryImpl
import com.jaycefr.jayto.domain.repository.PlaylistRepository
import com.jaycefr.jayto.domain.repository.SongRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JaytoDatabase {
        return Room.databaseBuilder(
            context,
            JaytoDatabase::class.java,
            JaytoDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideSongDao(database: JaytoDatabase): SongDao = database.songDao()

    @Provides
    fun providePlaylistDao(database: JaytoDatabase): PlaylistDao = database.playlistDao()

    @Provides
    @Singleton
    fun provideSongRepository(
        @ApplicationContext context: Context,
        songDao: SongDao
    ): SongRepository = SongRepositoryImpl(context, songDao)

    @Provides
    @Singleton
    fun providePlaylistRepository(
        playlistDao: PlaylistDao
    ): PlaylistRepository = PlaylistRepositoryImpl(playlistDao)
}
