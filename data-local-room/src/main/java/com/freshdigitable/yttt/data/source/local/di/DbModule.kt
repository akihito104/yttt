package com.freshdigitable.yttt.data.source.local.di

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.TwitchLiveLocalDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchScheduleDaoProviders
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamDaoProviders
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDaoProviders
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelDaoProviders
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistDaoProviders
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionDaoProviders
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoDaoProviders
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DbModule {
    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ytttdb")
            .build()

    @VisibleForTesting
    fun provideInMemoryDb(context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface LocalModule {
    @Binds
    fun bindYoutubeDataSourceLocal(dataSource: YouTubeLocalDataSource): YoutubeDataSource.Local

    @Binds
    fun bindTwitchDataSourceLocal(dataSource: TwitchLiveLocalDataSource): TwitchLiveDataSource.Local
}

@Module
@InstallIn(SingletonComponent::class)
internal interface TwitchDaoModule {
    @Binds
    fun bindTwitchUserDaoProvider(db: AppDatabase): TwitchUserDaoProviders

    @Binds
    fun bindTwitchScheduleDaoProvider(db: AppDatabase): TwitchScheduleDaoProviders

    @Binds
    fun bindTwitchStreamDaoProvider(db: AppDatabase): TwitchStreamDaoProviders
}

@Module
@InstallIn(SingletonComponent::class)
internal interface YouTubeDaoModule {
    @Binds
    fun bindYouTubeVideoDaoProvider(db: AppDatabase): YouTubeVideoDaoProviders

    @Binds
    fun bindYouTubeSubscriptionDaoProvider(db: AppDatabase): YouTubeSubscriptionDaoProviders

    @Binds
    fun bindYouTubePlaylistDaoProvider(db: AppDatabase): YouTubePlaylistDaoProviders

    @Binds
    fun bindYouTubeChannelDaoProvider(db: AppDatabase): YouTubeChannelDaoProviders
}
