package com.freshdigitable.yttt.data.source.local.di

import android.content.Context
import androidx.room.Room
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.TwitchLiveLocalDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object DbModule {
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ytttdb")
            .build()

    @Provides
    fun provideTwitchDao(database: AppDatabase): TwitchDao = database.twitchDao
}

@Module
@InstallIn(SingletonComponent::class)
internal interface LocalModule {
    @Binds
    fun bindYoutubeDataSourceLocal(dataSource: YouTubeLocalDataSource): YoutubeDataSource.Local

    @Binds
    fun bindTwitchDataSourceLocal(dataSource: TwitchLiveLocalDataSource): TwitchLiveDataSource.Local
}
