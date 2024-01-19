package com.freshdigitable.yttt.data.source.local.di

import android.content.Context
import androidx.room.Room
import com.freshdigitable.yttt.data.source.TwitchLiveDataSource
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.TableDeletable
import com.freshdigitable.yttt.data.source.local.TwitchLiveLocalDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource
import com.freshdigitable.yttt.data.source.local.db.FreeChatTable
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
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelAdditionTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelLogTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelTable
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistItemTable
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoExpireTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoIsArchivedTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoTable
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

@Qualifier
annotation class YouTubeQualifier

@Module
@InstallIn(SingletonComponent::class)
internal interface YouTubeDaoModule {
    companion object {
        @Provides
        fun provideYouTubeChannelDao(database: AppDatabase): YouTubeChannelTable.Dao =
            database.youTubeChannelDao

        @Provides
        fun provideYouTubeChannelAdditionDao(database: AppDatabase): YouTubeChannelAdditionTable.Dao =
            database.youTubeChannelAdditionDao

        @Provides
        fun provideYouTubeChannelLogDao(database: AppDatabase): YouTubeChannelLogTable.Dao =
            database.youTubeChannelLogDao

        @Provides
        fun provideYouTubePlaylistDao(database: AppDatabase): YouTubePlaylistTable.Dao =
            database.youTubePlaylistDao

        @Provides
        fun provideYouTubePlaylistItemDao(database: AppDatabase): YouTubePlaylistItemTable.Dao =
            database.youTubePlaylistItemDao

        @Provides
        fun provideYouTubeSubscriptionDao(database: AppDatabase): YouTubeSubscriptionTable.Dao =
            database.youTubeSubscriptionDao

        @Provides
        fun provideYouTubeVideoLogDao(database: AppDatabase): YouTubeVideoTable.Dao =
            database.youTubeVideoDao

        @Provides
        fun provideYouTubeVideoExpireDao(database: AppDatabase): YouTubeVideoExpireTable.Dao =
            database.youTubeVideoExpireDao

        @Provides
        fun provideYouTubeFreeChatDao(database: AppDatabase): FreeChatTable.Dao =
            database.youTubeFreeChatDao

        @Provides
        fun provideYouTubeVideoIsArchivedDao(database: AppDatabase): YouTubeVideoIsArchivedTable.Dao =
            database.youTubeVideoIsArchivedDao
    }

    @Binds
    @IntoSet
    @YouTubeQualifier
    fun bindYouTubeChannelDao(dao: YouTubeChannelTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @YouTubeQualifier
    fun bindYouTubeChannelAdditionDao(dao: YouTubeChannelAdditionTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @YouTubeQualifier
    fun bindYouTubeChannelLogDao(dao: YouTubeChannelLogTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @YouTubeQualifier
    fun bindYouTubePlaylistDao(dao: YouTubePlaylistTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @YouTubeQualifier
    fun bindYouTubePlaylistItemDao(dao: YouTubePlaylistItemTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @YouTubeQualifier
    fun bindYouTubeSubscriptionDao(dao: YouTubeSubscriptionTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @YouTubeQualifier
    fun bindYouTubeVideoDao(dao: YouTubeVideoTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @YouTubeQualifier
    fun bindYouTubeVideoExpireDao(dao: YouTubeVideoExpireTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @YouTubeQualifier
    fun bindYouTubeFreeChatDao(dao: FreeChatTable.Dao): TableDeletable

    @Binds
    @IntoSet
    @YouTubeQualifier
    fun bindYouTubeVideoIsArchivedDao(dao: YouTubeVideoIsArchivedTable.Dao): TableDeletable
}
