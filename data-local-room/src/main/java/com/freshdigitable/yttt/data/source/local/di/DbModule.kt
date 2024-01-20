package com.freshdigitable.yttt.data.source.local.di

import android.content.Context
import androidx.room.Room
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.TableDeletable
import com.freshdigitable.yttt.data.source.local.TwitchLiveLocalDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchAuthorizedUserTable
import com.freshdigitable.yttt.data.source.local.db.TwitchBroadcasterExpireTable
import com.freshdigitable.yttt.data.source.local.db.TwitchBroadcasterTable
import com.freshdigitable.yttt.data.source.local.db.TwitchChannelScheduleExpireTable
import com.freshdigitable.yttt.data.source.local.db.TwitchChannelVacationScheduleTable
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamExpireTable
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamScheduleTable
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamTable
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDetailExpireTable
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDetailTable
import com.freshdigitable.yttt.data.source.local.db.TwitchUserTable
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
import dagger.multibindings.IntoSet
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DbModule {
    @Singleton
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

@Qualifier
annotation class TwitchQualifier

@Module
@InstallIn(SingletonComponent::class)
internal interface TwitchDaoModule {
    companion object {
        @Provides
        fun provideTwitchUserTableDao(database: AppDatabase): TwitchUserTable.Dao =
            database.twitchUserDao

        @Provides
        fun provideTwitchUserDetailTableDao(database: AppDatabase): TwitchUserDetailTable.Dao =
            database.twitchUserDetailDao

        @Provides
        fun provideTwitchUserDetailExpireTableDao(database: AppDatabase): TwitchUserDetailExpireTable.Dao =
            database.twitchUserDetailExpireDao

        @Provides
        fun provideTwitchBroadcasterTableDao(database: AppDatabase): TwitchBroadcasterTable.Dao =
            database.twitchBroadcasterDao

        @Provides
        fun provideTwitchBroadcasterExpireTableDao(database: AppDatabase): TwitchBroadcasterExpireTable.Dao =
            database.twitchBroadcasterExpireDao

        @Provides
        fun provideTwitchAuthorizedUserTableDao(database: AppDatabase): TwitchAuthorizedUserTable.Dao =
            database.twitchAuthUserDao

        @Provides
        fun provideTwitchStreamTableDao(database: AppDatabase): TwitchStreamTable.Dao =
            database.twitchStreamDao

        @Provides
        fun provideTwitchStreamExpireTableDao(database: AppDatabase): TwitchStreamExpireTable.Dao =
            database.twitchStreamExpireDao

        @Provides
        fun provideTwitchStreamScheduleTableDao(database: AppDatabase): TwitchStreamScheduleTable.Dao =
            database.twitchChannelScheduleStreamDao

        @Provides
        fun provideTwitchVacationScheduleTableDao(database: AppDatabase): TwitchChannelVacationScheduleTable.Dao =
            database.twitchChannelScheduleVacationDao

        @Provides
        fun provideTwitchScheduleExpireTableDao(database: AppDatabase): TwitchChannelScheduleExpireTable.Dao =
            database.twitchChannelScheduleExpireDao
    }

    @Binds
    @IntoSet
    @TwitchQualifier
    fun bindTwitchUserTableDao(dao: TwitchUserTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @TwitchQualifier
    fun bindTwitchUserDetailTableDao(dao: TwitchUserDetailTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @TwitchQualifier
    fun bindTwitchUserDetailExpireTableDao(dao: TwitchUserDetailExpireTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @TwitchQualifier
    fun bindTwitchBroadcasterTableDao(dao: TwitchBroadcasterTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @TwitchQualifier
    fun bindTwitchBroadcasterExpireTableDao(dao: TwitchBroadcasterExpireTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @TwitchQualifier
    fun bindTwitchAuthorizedUserTableDao(dao: TwitchAuthorizedUserTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @TwitchQualifier
    fun bindTwitchStreamTableDao(dao: TwitchStreamTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @TwitchQualifier
    fun bindTwitchStreamExpireTableDao(dao: TwitchStreamExpireTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @TwitchQualifier
    fun bindTwitchStreamScheduleTableDao(dao: TwitchStreamScheduleTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @TwitchQualifier
    fun bindTwitchChannelVacationScheduleTableDao(dao: TwitchChannelVacationScheduleTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @TwitchQualifier
    fun bindTwitchChannelScheduleExpireTableDao(dao: TwitchChannelScheduleExpireTable.Dao): TableDeletable
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
