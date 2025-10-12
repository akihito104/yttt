package com.freshdigitable.yttt.data.source.local.di

import android.content.Context
import androidx.room.Room
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.source.LiveDataSource
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.TwitchScheduleDataSource
import com.freshdigitable.yttt.data.source.TwitchStreamDataSource
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.LiveLocalDataSource
import com.freshdigitable.yttt.data.source.local.TwitchExtendedDataSource
import com.freshdigitable.yttt.data.source.local.TwitchLocalDataSource
import com.freshdigitable.yttt.data.source.local.TwitchScheduleLocalDataSource
import com.freshdigitable.yttt.data.source.local.TwitchStreamLocalDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeExtendedDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource
import com.freshdigitable.yttt.data.source.local.db.LivePlatformConverter
import com.freshdigitable.yttt.data.source.local.db.TwitchScheduleDaoProviders
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamDaoProviders
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDaoProviders
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelDaoProviders
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistDaoProviders
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionDaoProviders
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoDaoProviders
import com.freshdigitable.yttt.di.ClassMap
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.jetbrains.annotations.VisibleForTesting
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DbModule {
    @Singleton
    @Provides
    internal fun provideDatabase(
        @ApplicationContext context: Context,
        flag: DbFlagModule.Flag? = null,
        platforms: ClassMap<LivePlatform, LivePlatform>,
    ): AppDatabase = if (flag == null) {
        AppDatabase.create(context, platforms = platforms.values)
    } else {
        AppDatabase.createInMemory(context, platforms.values)
    }
}

@VisibleForTesting
@Module
@InstallIn(SingletonComponent::class)
object DbFlagModule {
    enum class Flag { InMemory }

    @get:Provides
    internal val provideFlag: Flag? get() = null
}

private fun AppDatabase.Companion.createInMemory(context: Context, platforms: Collection<LivePlatform>) =
    Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .addTypeConverter(LivePlatformConverter(platforms))
        .build()

@Module
@InstallIn(SingletonComponent::class)
internal interface LocalModule {
    @Binds
    fun bindYouTubeDataSourceLocal(dataSource: YouTubeLocalDataSource): YouTubeDataSource.Local

    @Binds
    fun bindYouTubeExtendedDataSourceLocal(dataSource: YouTubeExtendedDataSource): YouTubeDataSource.Extended

    @Binds
    fun bindTwitchDataSourceLocal(dataSource: TwitchLocalDataSource): TwitchDataSource.Local

    @Binds
    fun bindTwitchExtendedDataSource(dataSource: TwitchExtendedDataSource): TwitchDataSource.Extended

    @Binds
    fun bindTwitchStreamLocalDataSource(dataSource: TwitchStreamLocalDataSource): TwitchStreamDataSource.Extended

    @Binds
    fun bindTwitchScheduleLocalDataSource(dataSource: TwitchScheduleLocalDataSource): TwitchScheduleDataSource.Extended

    @Binds
    fun bindLiveDataSource(dataSource: LiveLocalDataSource): LiveDataSource
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
