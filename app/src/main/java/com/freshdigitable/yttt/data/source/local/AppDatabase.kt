package com.freshdigitable.yttt.data.source.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.freshdigitable.yttt.data.model.CsvConverter
import com.freshdigitable.yttt.data.model.TwitchBroadcasterTable
import com.freshdigitable.yttt.data.model.TwitchChannelVacationScheduleTable
import com.freshdigitable.yttt.data.model.TwitchStreamDbView
import com.freshdigitable.yttt.data.model.TwitchStreamIdConverter
import com.freshdigitable.yttt.data.model.TwitchStreamScheduleIdConverter
import com.freshdigitable.yttt.data.model.TwitchStreamScheduleTable
import com.freshdigitable.yttt.data.model.TwitchStreamTable
import com.freshdigitable.yttt.data.model.TwitchUserDetailTable
import com.freshdigitable.yttt.data.model.TwitchUserIdConverter
import com.freshdigitable.yttt.data.model.TwitchUserTable
import com.freshdigitable.yttt.data.source.local.db.AppDao
import com.freshdigitable.yttt.data.source.local.db.BigIntegerConverter
import com.freshdigitable.yttt.data.source.local.db.DurationConverter
import com.freshdigitable.yttt.data.source.local.db.FreeChatTable
import com.freshdigitable.yttt.data.source.local.db.InstantConverter
import com.freshdigitable.yttt.data.source.local.db.LiveChannelAdditionTable
import com.freshdigitable.yttt.data.source.local.db.LiveChannelDetailDbView
import com.freshdigitable.yttt.data.source.local.db.LiveChannelIdConverter
import com.freshdigitable.yttt.data.source.local.db.LiveChannelLogIdConverter
import com.freshdigitable.yttt.data.source.local.db.LiveChannelLogTable
import com.freshdigitable.yttt.data.source.local.db.LiveChannelTable
import com.freshdigitable.yttt.data.source.local.db.LivePlaylistIdConverter
import com.freshdigitable.yttt.data.source.local.db.LivePlaylistItemDb
import com.freshdigitable.yttt.data.source.local.db.LivePlaylistItemIdConverter
import com.freshdigitable.yttt.data.source.local.db.LivePlaylistItemTable
import com.freshdigitable.yttt.data.source.local.db.LivePlaylistTable
import com.freshdigitable.yttt.data.source.local.db.LiveSubscriptionDbView
import com.freshdigitable.yttt.data.source.local.db.LiveSubscriptionIdConverter
import com.freshdigitable.yttt.data.source.local.db.LiveSubscriptionTable
import com.freshdigitable.yttt.data.source.local.db.LiveVideoDbView
import com.freshdigitable.yttt.data.source.local.db.LiveVideoExpireTable
import com.freshdigitable.yttt.data.source.local.db.LiveVideoIdConverter
import com.freshdigitable.yttt.data.source.local.db.LiveVideoTable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Database(
    entities = [
        LiveChannelTable::class,
        LiveChannelAdditionTable::class,
        LiveChannelLogTable::class,
        LiveSubscriptionTable::class,
        LiveVideoTable::class,
        FreeChatTable::class,
        LiveVideoExpireTable::class,
        LivePlaylistTable::class,
        LivePlaylistItemTable::class,
        TwitchUserTable::class,
        TwitchStreamScheduleTable::class,
        TwitchBroadcasterTable::class,
        TwitchStreamTable::class,
        TwitchUserDetailTable::class,
        TwitchChannelVacationScheduleTable::class,
    ],
    views = [
        LiveVideoDbView::class,
        LiveSubscriptionDbView::class,
        LiveChannelDetailDbView::class,
        LivePlaylistItemDb::class,
        TwitchStreamDbView::class,
    ],
    version = 9,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
    ]
)
@TypeConverters(
    InstantConverter::class,
    DurationConverter::class,
    LiveChannelIdConverter::class,
    LiveSubscriptionIdConverter::class,
    LiveVideoIdConverter::class,
    LiveChannelLogIdConverter::class,
    LivePlaylistIdConverter::class,
    LivePlaylistItemIdConverter::class,
    BigIntegerConverter::class,
    TwitchUserIdConverter::class,
    TwitchStreamScheduleIdConverter::class,
    TwitchStreamIdConverter::class,
    CsvConverter::class,
)
abstract class AppDatabase : RoomDatabase() {
    abstract val dao: AppDao
}

@Module
@InstallIn(SingletonComponent::class)
object DbModule {
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ytttdb")
            .build()
}
