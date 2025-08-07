package com.freshdigitable.yttt.data.source.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.freshdigitable.yttt.data.source.local.db.BigIntegerConverter
import com.freshdigitable.yttt.data.source.local.db.CsvConverter
import com.freshdigitable.yttt.data.source.local.db.DurationConverter
import com.freshdigitable.yttt.data.source.local.db.FreeChatTable
import com.freshdigitable.yttt.data.source.local.db.InstantConverter
import com.freshdigitable.yttt.data.source.local.db.TwitchAuthorizedUserTable
import com.freshdigitable.yttt.data.source.local.db.TwitchBroadcasterExpireTable
import com.freshdigitable.yttt.data.source.local.db.TwitchBroadcasterTable
import com.freshdigitable.yttt.data.source.local.db.TwitchCategoryIdConverter
import com.freshdigitable.yttt.data.source.local.db.TwitchCategoryTable
import com.freshdigitable.yttt.data.source.local.db.TwitchChannelScheduleExpireTable
import com.freshdigitable.yttt.data.source.local.db.TwitchChannelVacationScheduleTable
import com.freshdigitable.yttt.data.source.local.db.TwitchDaoProviders
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamExpireTable
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamIdConverter
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamScheduleIdConverter
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamScheduleTable
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamTable
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDetailDbView
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDetailExpireTable
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDetailTable
import com.freshdigitable.yttt.data.source.local.db.TwitchUserIdConverter
import com.freshdigitable.yttt.data.source.local.db.TwitchUserTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelAdditionExpireTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelAdditionTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelIdConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelLogIdConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelLogTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelRelatedPlaylistTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeDaoProviders
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistExpireTable
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistIdConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistItemAdditionTable
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistItemIdConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistItemTable
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistTable
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistWithItemsEtag
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionEtagTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionIdConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionRelevanceOrderTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoBroadcastType
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoExpireTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoIdConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoIsArchivedTable
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoTable

@Database(
    entities = [
        YouTubeChannelTable::class,
        YouTubeChannelAdditionTable::class,
        YouTubeChannelRelatedPlaylistTable::class,
        YouTubeChannelAdditionExpireTable::class,
        YouTubeChannelLogTable::class,
        YouTubeSubscriptionTable::class,
        YouTubeSubscriptionRelevanceOrderTable::class,
        YouTubeSubscriptionEtagTable::class,
        YouTubeVideoTable::class,
        YouTubeVideoIsArchivedTable::class,
        FreeChatTable::class,
        YouTubeVideoExpireTable::class,
        YouTubePlaylistTable::class,
        YouTubePlaylistExpireTable::class,
        YouTubePlaylistWithItemsEtag::class,
        YouTubePlaylistItemTable::class,
        YouTubePlaylistItemAdditionTable::class,
        TwitchUserTable::class,
        TwitchUserDetailTable::class,
        TwitchUserDetailExpireTable::class,
        TwitchBroadcasterTable::class,
        TwitchBroadcasterExpireTable::class,
        TwitchAuthorizedUserTable::class,
        TwitchStreamTable::class,
        TwitchStreamExpireTable::class,
        TwitchStreamScheduleTable::class,
        TwitchCategoryTable::class,
        TwitchChannelVacationScheduleTable::class,
        TwitchChannelScheduleExpireTable::class,
    ],
    views = [
        TwitchUserDetailDbView::class,
    ],
    version = 24,
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
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(
            from = 14,
            to = 15,
            spec = AppDatabase.MigrateRemoveTwitchUserDetailViewsCount::class,
        ),
        AutoMigration(from = 16, to = 17, spec = AppDatabase.MigrateRenameExpiredAt::class),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 21, to = 22, spec = AppDatabase.MigrateSeparatePlaylistItem::class),
        AutoMigration(from = 22, to = 23, spec = AppDatabase.MigrateSubscriptionOrder::class),
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
    YouTubeVideoBroadcastType::class,
    BigIntegerConverter::class,
    TwitchUserIdConverter::class,
    TwitchStreamScheduleIdConverter::class,
    TwitchStreamIdConverter::class,
    TwitchCategoryIdConverter::class,
    CsvConverter::class,
)
internal abstract class AppDatabase : RoomDatabase(), TwitchDaoProviders, YouTubeDaoProviders {
    @DeleteColumn.Entries(DeleteColumn(tableName = "video", columnName = "visible"))
    internal class MigrateRemoveVideoVisible : AutoMigrationSpec

    @DeleteColumn.Entries(
        DeleteColumn(
            tableName = "twitch_user_detail",
            columnName = "views_count",
        ),
    )
    internal class MigrateRemoveTwitchUserDetailViewsCount : AutoMigrationSpec

    @DeleteColumn.Entries(
        DeleteColumn(
            tableName = "channel_addition_expire",
            columnName = "expired_at",
        ),
        DeleteColumn(
            tableName = "video_expire",
            columnName = "expired_at",
        ),
        DeleteColumn(
            tableName = "twitch_user_detail_expire",
            columnName = "expired_at",
        ),
        DeleteColumn(
            tableName = "twitch_channel_schedule_expire",
            columnName = "expired_at",
        ),
        DeleteColumn(
            tableName = "twitch_stream_expire",
            columnName = "expired_at",
        ),
        DeleteColumn(
            tableName = "twitch_broadcaster_expire",
            columnName = "expire_at",
        ),
    )
    internal class MigrateRenameExpiredAt : AutoMigrationSpec

    @DeleteColumn.Entries(
        DeleteColumn(tableName = "playlist_item", columnName = "title"),
        DeleteColumn(tableName = "playlist_item", columnName = "channel_id"),
        DeleteColumn(tableName = "playlist_item", columnName = "thumbnail_url"),
        DeleteColumn(tableName = "playlist_item", columnName = "description"),
        DeleteColumn(tableName = "playlist_item", columnName = "video_owner_channel_id"),
    )
    internal class MigrateSeparatePlaylistItem : AutoMigrationSpec

    @DeleteColumn.Entries(
        DeleteColumn(tableName = "subscription", columnName = "subs_order")
    )
    internal class MigrateSubscriptionOrder : AutoMigrationSpec
    companion object {
        private const val DATABASE_NAME = "ytttdb"
        internal fun create(context: Context, name: String = DATABASE_NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_13_14, MIGRATION_15_16, MIGRATION_18_19, MIGRATION_23_24)
                .build()
    }
}

internal fun AppDatabase.deferForeignKeys() {
    query("PRAGMA defer_foreign_keys = TRUE", null)
}

internal interface TableDeletable {
    suspend fun deleteTable()
}

internal val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `twitch_category` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`art_url_base` TEXT, `igdb_id` TEXT, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "INSERT INTO twitch_category SELECT category_id AS id, category_name AS name, null, null " +
                "FROM twitch_channel_schedule_stream WHERE category_id IS NOT NULL GROUP BY category_id"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `__twitch_channel_schedule_stream` (`id` TEXT NOT NULL, " +
                "`start_time` INTEGER NOT NULL, `end_time` INTEGER, `title` TEXT NOT NULL, `canceled_until` TEXT, " +
                "`category_id` TEXT, `is_recurring` INTEGER NOT NULL, `user_id` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`user_id`) REFERENCES `twitch_user`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(`category_id`) REFERENCES `twitch_category`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        db.execSQL(
            "INSERT INTO __twitch_channel_schedule_stream (id, start_time, end_time, title, canceled_until, category_id, is_recurring, user_id) " +
                "SELECT id, start_time, end_time, title, canceled_until, category_id, is_recurring, user_id FROM twitch_channel_schedule_stream"
        )
        db.execSQL("DROP TABLE twitch_channel_schedule_stream")
        db.execSQL("ALTER TABLE __twitch_channel_schedule_stream RENAME TO twitch_channel_schedule_stream")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_twitch_channel_schedule_stream_category_id` ON `twitch_channel_schedule_stream` (`category_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_twitch_channel_schedule_stream_user_id` ON `twitch_channel_schedule_stream` (`user_id`)")
        db.foreignKeyCheck("twitch_channel_schedule_stream")
    }
}

internal val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT OR IGNORE INTO twitch_category (id, name) SELECT game_id AS id, game_name AS name " +
                "FROM twitch_stream WHERE game_id IS NOT NULL GROUP BY game_id"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `__twitch_stream` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `thumbnail_url_base` TEXT NOT NULL, `view_count` INTEGER NOT NULL, " +
                "`language` TEXT NOT NULL, `game_id` TEXT NOT NULL, `type` TEXT NOT NULL, `started_at` INTEGER NOT NULL, " +
                "`tags` TEXT NOT NULL, `is_mature` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`user_id`) REFERENCES `twitch_user`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(`game_id`) REFERENCES `twitch_category`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        db.execSQL(
            "INSERT INTO __twitch_stream (id, user_id, title, thumbnail_url_base, view_count, " +
                "language, game_id, type, started_at, tags, is_mature) SELECT id, user_id, title, " +
                "thumbnail_url_base, view_count, language, game_id, type, started_at, tags, is_mature FROM twitch_stream"
        )
        db.execSQL("DROP TABLE twitch_stream")
        db.execSQL("ALTER TABLE __twitch_stream RENAME TO twitch_stream")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_twitch_stream_user_id` ON `twitch_stream` (`user_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_twitch_stream_game_id` ON `twitch_stream` (`game_id`)")
        db.foreignKeyCheck("twitch_stream")
    }
}

internal val MIGRATION_18_19 = object : Migration(18, 19) {
    // playlist(18).id -> playlist(19).(id,title,thumbnail_url)
    // playlist(18).(id,last_modified,max_age) -> playlist_expire.(playlist_id,fetched_at,max_age)
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `__playlist` (`id` TEXT NOT NULL, `title` TEXT NOT NULL DEFAULT '', " +
                "`thumbnail_url` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `__playlist_expire` (`playlist_id` TEXT NOT NULL, " +
                "`fetched_at` INTEGER DEFAULT null, `max_age` INTEGER DEFAULT null, " +
                "PRIMARY KEY(`playlist_id`), " +
                "FOREIGN KEY(`playlist_id`) REFERENCES `playlist`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )

        db.execSQL("INSERT INTO __playlist (id) SELECT id FROM playlist")
        db.execSQL(
            "INSERT INTO __playlist_expire (playlist_id, fetched_at, max_age) " +
                "SELECT id, last_modified, max_age FROM playlist"
        )

        db.execSQL("DROP TABLE playlist")
        db.execSQL("ALTER TABLE __playlist RENAME TO playlist")
        db.execSQL("ALTER TABLE __playlist_expire RENAME TO playlist_expire")
        db.foreignKeyCheck("playlist_expire")
    }
}

internal val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP VIEW twitch_user_detail_view")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `yt_channel_related_playlist` (`channel_id` TEXT NOT NULL, " +
                "`uploaded_playlist_id` TEXT, PRIMARY KEY(`channel_id`), " +
                "FOREIGN KEY(`channel_id`) REFERENCES `channel`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, " +
                "FOREIGN KEY(`uploaded_playlist_id`) REFERENCES `playlist`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_yt_channel_related_playlist_uploaded_playlist_id`" +
                " ON `yt_channel_related_playlist` (`uploaded_playlist_id`)"
        )
        db.execSQL(
            "INSERT INTO `yt_channel_related_playlist` (`channel_id`, `uploaded_playlist_id`) " +
                "SELECT `id` AS `channel_id`, `uploaded_playlist_id` FROM `channel_addition`"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_channel_addition` (`id` TEXT NOT NULL, `banner_url` TEXT, " +
                "`subscriber_count` INTEGER NOT NULL, `is_subscriber_hidden` INTEGER NOT NULL, " +
                "`video_count` INTEGER NOT NULL, `view_count` INTEGER NOT NULL, `published_at` INTEGER NOT NULL, " +
                "`custom_url` TEXT NOT NULL, `keywords` TEXT NOT NULL, `description` TEXT, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`id`) REFERENCES `channel`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        )
        db.execSQL(
            "INSERT INTO `_new_channel_addition` (`id`,`banner_url`,`subscriber_count`,`is_subscriber_hidden`," +
                "`video_count`,`view_count`,`published_at`,`custom_url`,`keywords`,`description`) " +
                "SELECT `id`,`banner_url`,`subscriber_count`,`is_subscriber_hidden`,`video_count`,`view_count`," +
                "`published_at`,`custom_url`,`keywords`,`description` FROM `channel_addition`"
        )
        db.execSQL("DROP TABLE `channel_addition`")
        db.execSQL("ALTER TABLE `_new_channel_addition` RENAME TO `channel_addition`")
        db.foreignKeyCheck("channel_addition")
        db.execSQL(
            "CREATE VIEW `twitch_user_detail_view` AS SELECT u.login_name, u.display_name, d.* " +
                "FROM twitch_user_detail AS d INNER JOIN twitch_user AS u ON d.user_id = u.id"
        )
    }
}

internal fun SupportSQLiteDatabase.foreignKeyCheck(tableName: String) {
    query("PRAGMA foreign_key_check('$tableName')").use {
        if (it.count > 0) {
            val msg = buildString {
                while (it.moveToNext()) {
                    if (it.isFirst) {
                        append("foreign key violation: ")
                        append(it.getString(0)).append("\n")
                    }
                    append(it.getString(3)).append(",").append(it.getString(2)).append("\n")
                }
            }
            throw SQLiteConstraintException(msg)
        }
    }
}
