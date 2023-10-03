package com.freshdigitable.yttt.data.source.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.freshdigitable.yttt.data.source.local.db.AppDao
import com.freshdigitable.yttt.data.source.local.db.FreeChatTable
import com.freshdigitable.yttt.data.source.local.db.LiveChannelAdditionTable
import com.freshdigitable.yttt.data.source.local.db.LiveChannelDetailDbView
import com.freshdigitable.yttt.data.source.local.db.LiveChannelLogTable
import com.freshdigitable.yttt.data.source.local.db.LiveChannelTable
import com.freshdigitable.yttt.data.source.local.db.LiveSubscriptionDbView
import com.freshdigitable.yttt.data.source.local.db.LiveSubscriptionTable
import com.freshdigitable.yttt.data.source.local.db.LiveVideoDbView
import com.freshdigitable.yttt.data.source.local.db.LiveVideoExpireTable
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
    ],
    views = [
        LiveVideoDbView::class,
        LiveSubscriptionDbView::class,
        LiveChannelDetailDbView::class,
    ],
    version = 8,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
    ]
)
@TypeConverters(
    InstantConverter::class,
    LiveChannelIdConverter::class,
    LiveSubscriptionIdConverter::class,
    LiveVideoIdConverter::class,
    LiveChannelLogIdConverter::class,
    LivePlaylistIdConverter::class,
    BigIntegerConverter::class,
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
