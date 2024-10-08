package com.freshdigitable.yttt.data.source.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import com.freshdigitable.yttt.data.source.local.db.BigIntegerConverter
import com.freshdigitable.yttt.data.source.local.db.CsvConverter
import com.freshdigitable.yttt.data.source.local.db.DurationConverter
import com.freshdigitable.yttt.data.source.local.db.FreeChatTable
import com.freshdigitable.yttt.data.source.local.db.InstantConverter
import com.freshdigitable.yttt.data.source.local.db.TwitchAuthorizedUserTable
import com.freshdigitable.yttt.data.source.local.db.TwitchBroadcasterExpireTable
import com.freshdigitable.yttt.data.source.local.db.TwitchBroadcasterTable
import com.freshdigitable.yttt.data.source.local.db.TwitchChannelScheduleExpireTable
import com.freshdigitable.yttt.data.source.local.db.TwitchChannelVacationScheduleTable
import com.freshdigitable.yttt.data.source.local.db.TwitchDaoProviders
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamDbView
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamExpireTable
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamIdConverter
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamScheduleIdConverter
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamScheduleTable
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamTable
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDetailExpireTable
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDetailTable
import com.freshdigitable.yttt.data.source.local.db.TwitchUserIdConverter
import com.freshdigitable.yttt.data.source.local.db.TwitchUserTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelAdditionExpireTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelAdditionTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelIdConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelLogIdConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelLogTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeDaoProviders
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistIdConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistItemIdConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistItemSummaryDb
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistItemTable
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionIdConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoExpireTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoIdConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoIsArchivedTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoTable

@Database(
    entities = [
        YouTubeChannelTable::class,
        YouTubeChannelAdditionTable::class,
        YouTubeChannelAdditionExpireTable::class,
        YouTubeChannelLogTable::class,
        YouTubeSubscriptionTable::class,
        YouTubeVideoTable::class,
        YouTubeVideoIsArchivedTable::class,
        FreeChatTable::class,
        YouTubeVideoExpireTable::class,
        YouTubePlaylistTable::class,
        YouTubePlaylistItemTable::class,
        TwitchUserTable::class,
        TwitchUserDetailTable::class,
        TwitchUserDetailExpireTable::class,
        TwitchBroadcasterTable::class,
        TwitchBroadcasterExpireTable::class,
        TwitchAuthorizedUserTable::class,
        TwitchStreamTable::class,
        TwitchStreamExpireTable::class,
        TwitchStreamScheduleTable::class,
        TwitchChannelVacationScheduleTable::class,
        TwitchChannelScheduleExpireTable::class,
    ],
    views = [
        YouTubePlaylistItemSummaryDb::class,
        TwitchStreamDbView::class,
    ],
    version = 11,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10, spec = AppDatabase.MigrateRemoveVideoVisible::class),
        AutoMigration(from = 10, to = 11),
    ]
)
@TypeConverters(
    InstantConverter::class,
    DurationConverter::class,
    YouTubeSubscriptionIdConverter::class,
    YouTubeChannelLogIdConverter::class,
    YouTubePlaylistIdConverter::class,
    YouTubePlaylistItemIdConverter::class,
    YouTubeVideoIdConverter::class,
    YouTubeChannelIdConverter::class,
    BigIntegerConverter::class,
    TwitchUserIdConverter::class,
    TwitchStreamScheduleIdConverter::class,
    TwitchStreamIdConverter::class,
    CsvConverter::class,
)
internal abstract class AppDatabase : RoomDatabase(), TwitchDaoProviders, YouTubeDaoProviders {
    @DeleteColumn.Entries(DeleteColumn(tableName = "video", columnName = "visible"))
    internal class MigrateRemoveVideoVisible : AutoMigrationSpec
}

internal fun AppDatabase.deferForeignKeys() {
    query("PRAGMA defer_foreign_keys = TRUE", null)
}

internal interface TableDeletable {
    suspend fun deleteTable()
}
