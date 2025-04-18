package com.freshdigitable.yttt.data.source.local.di

import android.content.Context
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.TwitchLocalDataSource
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
        AppDatabase.create(context)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface LocalModule {
    @Binds
    fun bindYouTubeDataSourceLocal(dataSource: YouTubeLocalDataSource): YouTubeDataSource.Local

    @Binds
    fun bindTwitchDataSourceLocal(dataSource: TwitchLocalDataSource): TwitchDataSource.Local

    @Binds
    fun bindTwitchLiveDataSourceLocal(dataSource: TwitchLocalDataSource): TwitchLiveDataSource.Local
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
