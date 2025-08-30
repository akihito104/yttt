package com.freshdigitable.yttt.data.source.local

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.freshdigitable.yttt.data.source.local.fixture.AppMigrationTestRule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import io.kotest.matchers.string.shouldBeEmpty as shouldBeEmptyString

@RunWith(Enclosed::class)
class AppDatabaseMigrationTest {
    private companion object {
        private const val TABLE_STREAM_SCHEDULE = "twitch_channel_schedule_stream"
        private const val TABLE_CATEGORY = "twitch_category"
    }

    class From13To14 {
        private val schedule = listOf(
            twitchChannelStreamSchedule13(0, "cid0", "0"),
            twitchChannelStreamSchedule13(1, null, "1"),
            twitchChannelStreamSchedule13(2, "cid0", "2"),
            twitchChannelStreamSchedule13(3, "cid1", "3")
        )

        @get:Rule
        internal val rule = AppMigrationTestRule(13, 14, MIGRATION_13_14)

        @Before
        fun setup(): Unit = rule.oldDb.use {
            rule.insertForSetup(
                "twitch_user" to (0..3).map { twitchUser(it) },
                TABLE_STREAM_SCHEDULE to schedule,
            )
        }

        @Test
        fun init(): Unit = rule.run {
            // verify
            newDb.query("SELECT * FROM $TABLE_STREAM_SCHEDULE").use {
                it.count shouldBe schedule.size
            }
            newDb.query("SELECT * FROM $TABLE_CATEGORY").use { c ->
                c.count shouldBe 2
                listOf(schedule[0], schedule[3]).forEach {
                    c.moveToNext()
                    c.getString(0) shouldBe it.getAsString("category_id")
                    c.getString(1) shouldBe it.getAsString("category_name")
                }
            }
        }

        @Test
        fun insert(): Unit = rule.run {
            // setup
            val values = twitchChannelStreamSchedule14(99, "cid0", "3")
            // exercise
            val actual =
                newDb.insert(TABLE_STREAM_SCHEDULE, SQLiteDatabase.CONFLICT_ABORT, values)
            // verify
            actual shouldBeGreaterThan -1L
        }

        @Test
        fun insert_throwConstraintExceptionForCategoryId(): Unit = rule.run {
            // setup
            val values = twitchChannelStreamSchedule14(99, "cid99", "3")
            // exercise
            shouldThrow<SQLiteConstraintException> {
                newDb.insert(TABLE_STREAM_SCHEDULE, SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }

        @Test
        fun insert_throwConstraintExceptionForUserId(): Unit = rule.run {
            // setup
            val values = twitchChannelStreamSchedule14(99, "cid1", "99")
            // exercise
            shouldThrow<SQLiteConstraintException> {
                newDb.insert(TABLE_STREAM_SCHEDULE, SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
    }

    class From15To16 {
        private val user = (0..3).map { twitchUser(it) }
        private val category = listOf(
            twitchCategory(0),
            twitchCategory(5),
        )
        private val stream = listOf(
            twitchStream15(0, user[0].getAsString("id"), category[0].getAsString("id")),
            twitchStream15(1, user[1].getAsString("id"), "1"),
            twitchStream15(2, user[2].getAsString("id"), "1"),
            twitchStream15(3, user[3].getAsString("id"), "2"),
        )

        @get:Rule
        internal val rule = AppMigrationTestRule(15, 16, MIGRATION_15_16)

        @Before
        fun setup(): Unit = rule.oldDb.use {
            rule.insertForSetup(
                "twitch_user" to user,
                TABLE_CATEGORY to category,
                "twitch_stream" to stream,
            )
        }

        @Test
        fun init(): Unit = rule.run {
            // verify
            newDb.query("SELECT * FROM twitch_stream").use {
                it.count shouldBe stream.size
            }
            newDb.query("SELECT * FROM $TABLE_CATEGORY").use {
                it.count shouldBe (category.size + 2)
            }
            newDb.query("SELECT * FROM $TABLE_CATEGORY WHERE id = '0'").use {
                it.moveToNext()
                listOf("id", "name", "art_url_base", "igdb_id").forEachIndexed { i, key ->
                    it.getString(i) shouldBe category[0].getAsString(key)
                }
            }
        }

        @Test
        fun insert(): Unit = rule.run {
            // setup
            val values = twitchStream16(99, user[0].getAsString("id"), "0")
            // exercise
            val actual = newDb.insert("twitch_stream", SQLiteDatabase.CONFLICT_ABORT, values)
            // verify
            actual shouldBeGreaterThan -1L
        }

        @Test
        fun insert_throwsConstraintExceptionForUserId(): Unit = rule.run {
            // setup
            val values = twitchStream16(99, "99", "0")
            // exercise
            shouldThrow<SQLiteConstraintException> {
                newDb.insert("twitch_stream", SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }

        @Test
        fun insert_throwsConstraintExceptionForCategoryId(): Unit = rule.run {
            // setup
            val values = twitchStream16(99, user[0].getAsString("id"), "99")
            // exercise
            shouldThrow<SQLiteConstraintException> {
                newDb.insert("twitch_stream", SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
    }

    class From18To19 {
        @get:Rule
        internal val rule = AppMigrationTestRule(18, 19, MIGRATION_18_19)
        private val playlist = (0..2).map { youtubePlaylist18(it) }

        @Before
        fun setup(): Unit = rule.oldDb.use {
            rule.insertForSetup("playlist" to playlist)
        }

        @Test
        fun init(): Unit = rule.run {
            newDb.query("SELECT * FROM playlist").use { c ->
                c.count shouldBe 3
                playlist.forEachIndexed { i, value ->
                    c.moveToNext()
                    c.getString(0) shouldBe value.getAsString("id")
                    c.getString(1).shouldBeEmptyString()
                    c.getString(2).shouldBeEmptyString()
                }
            }
            newDb.query("SELECT * FROM playlist_expire").use { c ->
                c.count shouldBe 3
                playlist.forEachIndexed { i, value ->
                    c.moveToNext()
                    c.getString(0) shouldBe value.getAsString("id")
                    c.getLong(1) shouldBe value.getAsLong("last_modified")
                    c.getLong(2) shouldBe value.getAsLong("max_age")
                }
            }
        }

        @Test
        fun update(): Unit = rule.run {
            // setup
            val values = ContentValues().apply {
                put("fetched_at", 30000)
                put("max_age", 1000)
            }
            // exercise
            val playlistId0 = playlist[0].getAsString("id")
            val actual = newDb.update(
                "playlist_expire",
                SQLiteDatabase.CONFLICT_ABORT,
                values,
                "playlist_id = ?",
                arrayOf(playlistId0),
            )
            // verify
            actual shouldBe 1
            newDb.query("SELECT * FROM playlist_expire WHERE playlist_id = '$playlistId0'")
                .use { c ->
                    c.moveToNext()
                    c.getLong(1) shouldBe values.getAsLong("fetched_at")
                    c.getLong(2) shouldBe values.getAsLong("max_age")
                }
        }

        @Test
        fun insert(): Unit = rule.run {
            // setup
            val playlistData = ContentValues().apply {
                put("id", "10")
                put("title", "")
                put("thumbnail_url", "")
            }
            val playlistExpire = ContentValues().apply {
                put("playlist_id", "10")
                put("fetched_at", 30000)
                put("max_age", 1000)
            }
            // exercise
            val playlistActual =
                newDb.insert("playlist", SQLiteDatabase.CONFLICT_ABORT, playlistData)
            val playlistExpireActual =
                newDb.insert("playlist_expire", SQLiteDatabase.CONFLICT_ABORT, playlistExpire)
            // verify
            playlistActual shouldBeGreaterThan -1L
            playlistExpireActual shouldBeGreaterThan -1L
            newDb.query("SELECT * FROM playlist WHERE id = '10'").use { c ->
                c.moveToNext()
                c.getString(0) shouldBe playlistData.getAsString("id")
                c.getString(1) shouldBe playlistData.getAsString("title")
                c.getString(2) shouldBe playlistData.getAsString("thumbnail_url")
            }
            newDb.query("SELECT * FROM playlist_expire WHERE playlist_id = '10'").use { c ->
                c.moveToNext()
                c.getString(0) shouldBe playlistExpire.getAsString("playlist_id")
                c.getLong(1) shouldBe playlistExpire.getAsLong("fetched_at")
                c.getLong(2) shouldBe playlistExpire.getAsLong("max_age")
            }
        }

        @Test
        fun insert_throwsConstraintExceptionForPlaylistExpire(): Unit = rule.run {
            // setup
            val values = ContentValues().apply {
                put("playlist_id", "99")
                put("fetched_at", 30000)
                put("max_age", 1000)
            }
            // exercise
            shouldThrow<SQLiteConstraintException> {
                newDb.insert("playlist_expire", SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
    }

    class From23To24 {
        @get:Rule
        internal val rule = AppMigrationTestRule(23, 24, MIGRATION_23_24)

        @Before
        fun setup(): Unit = rule.oldDb.use {
            rule.insertForSetup("channel" to listOf(ContentValues().apply {
                put("id", "channel_0")
                put("title", "channel_title_0")
                put("icon", "")
            }))
            rule.insertForSetup("playlist" to listOf(ContentValues().apply {
                put("id", "channel_0-playlist_uploaded")
                put("title", "uploaded")
                put("thumbnail_url", "")
            }))
            rule.insertForSetup("channel_addition" to listOf(ContentValues().apply {
                put("id", "channel_0")
                put("banner_url", "")
                put("subscriber_count", "10")
                put("is_subscriber_hidden", "false")
                put("video_count", "4")
                put("view_count", "300")
                put("published_at", "0")
                put("custom_url", "@channel_0")
                put("keywords", "")
                put("description", "")
                put("uploaded_playlist_id", "channel_0-playlist_uploaded")
            }))
        }

        @Test
        fun init(): Unit = rule.run {
            newDb.query("SELECT * FROM yt_channel_related_playlist").use { c ->
                c.moveToNext()
                c.getString(0) shouldBe "channel_0"
                c.getString(1) shouldBe "channel_0-playlist_uploaded"
            }
            newDb.query("SELECT * FROM channel_addition").use { c ->
                c.moveToNext()
                shouldThrow<IllegalArgumentException> {
                    c.getColumnIndexOrThrow("uploaded_playlist_id")
                }
            }
        }

        @Test
        fun insert(): Unit = rule.run {
            newDb.insert("channel", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", "channel_99")
                put("title", "channel_99_title")
                put("icon", "")
            })
            newDb.insert("playlist", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", "channel_99-playlist_uploaded")
                put("title", "uploaded")
                put("thumbnail_url", "")
            })
            newDb.insert(
                "yt_channel_related_playlist", SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("channel_id", "channel_99")
                    put("uploaded_playlist_id", "channel_99-playlist_uploaded")
                })
            newDb.query("select * from yt_channel_related_playlist").use { c ->
                c.count shouldBe 2
            }
        }

        @Test
        fun insert_throwsConstraintExceptionForChannel(): Unit = rule.run {
            newDb.insert("playlist", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", "channel_99-playlist_uploaded")
                put("title", "uploaded")
                put("thumbnail_url", "")
            })
            shouldThrow<SQLiteConstraintException> {
                newDb.insert(
                    "yt_channel_related_playlist", SQLiteDatabase.CONFLICT_ABORT,
                    ContentValues().apply {
                        put("channel_id", "channel_99")
                        put("uploaded_playlist_id", "channel_99-playlist_uploaded")
                    })
            }
        }

        @Test
        fun insert_throwsConstraintExceptionForPlaylist(): Unit = rule.run {
            newDb.insert("channel", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", "channel_99")
                put("title", "channel_99_title")
                put("icon", "")
            })
            shouldThrow<SQLiteConstraintException> {
                newDb.insert(
                    "yt_channel_related_playlist", SQLiteDatabase.CONFLICT_ABORT,
                    ContentValues().apply {
                        put("channel_id", "channel_99")
                        put("uploaded_playlist_id", "channel_99-playlist_uploaded")
                    })
            }
        }
    }
}

fun twitchUser(id: Int): ContentValues = ContentValues().apply {
    put("id", "$id")
    put("login_name", "user_$id")
    put("display_name", "user_$id")
}

fun twitchChannelStreamSchedule13(id: Int, categoryId: String?, userId: String): ContentValues =
    ContentValues().apply {
        put("id", "$id")
        if (categoryId != null) {
            put("category_id", categoryId)
            put("category_name", "cname_$categoryId")
        }
        put("user_id", userId)
        put("start_time", 0)
        put("end_time", 60000)
        put("title", "title")
        putNull("canceled_until")
        put("is_recurring", 0)
    }

fun twitchChannelStreamSchedule14(id: Int, categoryId: String?, userId: String): ContentValues =
    twitchChannelStreamSchedule13(id, categoryId, userId).apply {
        if (categoryId != null) {
            put("category_id", categoryId)
            remove("category_name")
        }
    }

fun twitchStream15(id: Int, userId: String, categoryId: String): ContentValues =
    ContentValues().apply {
        put("id", "$id")
        put("user_id", userId)
        put("title", "title")
        put("thumbnail_url_base", "")
        put("view_count", 0)
        put("language", "ja")
        put("game_id", categoryId)
        put("game_name", "game_$categoryId")
        put("type", "type")
        put("started_at", 0)
        put("tags", "tag")
        put("is_mature", 0)
    }

fun twitchStream16(id: Int, userId: String, categoryId: String): ContentValues =
    twitchStream15(id, userId, categoryId).apply {
        remove("game_name")
    }

fun twitchCategory(id: Int): ContentValues = ContentValues().apply {
    put("id", "$id")
    put("name", "cname_$id")
    put("art_url_base", "<url is here>")
    put("igdb_id", "$id")
}

fun youtubePlaylist18(id: Int): ContentValues = ContentValues().apply {
    put("id", "$id")
    put("last_modified", 50 * id)
    put("max_age", 5 * id)
}
