package com.freshdigitable.yttt.data.source.local

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

@RunWith(Enclosed::class)
class AppDatabaseMigrationTest {
    private companion object {
        private const val TABLE_STREAM_SCHEDULE = "twitch_channel_schedule_stream"
        private const val TABLE_CATEGORY = "twitch_category"
    }

    @RunWith(AndroidJUnit4::class)
    class From13To14 {
        private companion object {
            private val schedule = listOf(
                twitchChannelStreamSchedule13(0, "cid0", "0"),
                twitchChannelStreamSchedule13(1, null, "1"),
                twitchChannelStreamSchedule13(2, "cid0", "2"),
                twitchChannelStreamSchedule13(3, "cid1", "3")
            )
        }

        @get:Rule
        val rule = AppMigrationTestRule(13, 14, MIGRATION_13_14)

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
                assertThat(it.count).isEqualTo(schedule.size)
            }
            newDb.query("SELECT * FROM $TABLE_CATEGORY").use { c ->
                assertThat(c.count).isEqualTo(2)
                listOf(schedule[0], schedule[3]).forEach {
                    c.moveToNext()
                    assertThat(c.getString(0)).isEqualTo(it.getAsString("category_id"))
                    assertThat(c.getString(1)).isEqualTo(it.getAsString("category_name"))
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
            assertThat(actual).isGreaterThan(-1)
        }

        @Test
        fun insert_throwConstraintExceptionForCategoryId(): Unit = rule.run {
            // setup
            val values = twitchChannelStreamSchedule14(99, "cid99", "3")
            // exercise
            assertThatThrownBy {
                newDb.insert(TABLE_STREAM_SCHEDULE, SQLiteDatabase.CONFLICT_ABORT, values)
                // verify
            }.isInstanceOf(SQLiteConstraintException::class.java)
        }

        @Test
        fun insert_throwConstraintExceptionForUserId(): Unit = rule.run {
            // setup
            val values = twitchChannelStreamSchedule14(99, "cid1", "99")
            // exercise
            assertThatThrownBy {
                newDb.insert(TABLE_STREAM_SCHEDULE, SQLiteDatabase.CONFLICT_ABORT, values)
                // verify
            }.isInstanceOf(SQLiteConstraintException::class.java)
        }
    }

    @RunWith(AndroidJUnit4::class)
    class From15To16 {
        companion object {
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
        }

        @get:Rule
        val rule = AppMigrationTestRule(15, 16, MIGRATION_15_16)

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
                assertThat(it.count).isEqualTo(stream.size)
            }
            newDb.query("SELECT * FROM $TABLE_CATEGORY").use {
                assertThat(it.count).isEqualTo(category.size + 2)
            }
            newDb.query("SELECT * FROM $TABLE_CATEGORY WHERE id = '0'").use {
                it.moveToNext()
                listOf("id", "name", "art_url_base", "igdb_id").forEachIndexed { i, key ->
                    assertThat(it.getString(i)).isEqualTo(category[0].getAsString(key))
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
            assertThat(actual).isGreaterThan(-1)
        }

        @Test
        fun insert_throwsConstraintExceptionForUserId(): Unit = rule.run {
            // setup
            val values = twitchStream16(99, "99", "0")
            // exercise
            assertThatThrownBy {
                newDb.insert("twitch_stream", SQLiteDatabase.CONFLICT_ABORT, values)
                // verify
            }.isInstanceOf(SQLiteConstraintException::class.java)
        }

        @Test
        fun insert_throwsConstraintExceptionForCategoryId(): Unit = rule.run {
            // setup
            val values = twitchStream16(99, user[0].getAsString("id"), "99")
            // exercise
            assertThatThrownBy {
                newDb.insert("twitch_stream", SQLiteDatabase.CONFLICT_ABORT, values)
                // verify
            }.isInstanceOf(SQLiteConstraintException::class.java)
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
    put("name", "cname_0")
    put("art_url_base", "<url is here>")
    put("igdb_id", "0")
}
