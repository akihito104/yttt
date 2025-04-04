package com.freshdigitable.yttt.data.source.local

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val rule = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )
    private val schedule = listOf(
        twitchChannelStreamSchedule13(0, "cid0", "0"),
        twitchChannelStreamSchedule13(1, null, "1"),
        twitchChannelStreamSchedule13(2, "cid0", "2"),
        twitchChannelStreamSchedule13(3, "cid1", "3")
    )

    @Before
    fun setup() {
        rule.createDatabase("test", 13).use { db ->
            val userRes = (0..3).map {
                db.insert("twitch_user", SQLiteDatabase.CONFLICT_ABORT, twitchUser(it))
            }
            assertThat(userRes).allMatch { it > -1 }
            val scheduleRes = schedule.map {
                db.insert("twitch_channel_schedule_stream", SQLiteDatabase.CONFLICT_ABORT, it)
            }
            assertThat(scheduleRes).allMatch { it > -1 }
        }
    }

    private val newDb: SupportSQLiteDatabase by lazy {
        rule.runMigrationsAndValidate("test", 14, true, MIGRATION_13_14).also {
            it.query("PRAGMA foreign_keys = ON")
        }
    }

    @After
    fun tearDown() {
        newDb.close()
    }

    @Test
    fun init() {
        // verify
        newDb.query("SELECT * FROM twitch_channel_schedule_stream").use {
            assertThat(it.count).isEqualTo(schedule.size)
        }
        newDb.query("SELECT * FROM twitch_category").use { c ->
            assertThat(c.count).isEqualTo(2)
            listOf(schedule[0], schedule[3]).forEach {
                c.moveToNext()
                assertThat(c.getString(0)).isEqualTo(it.getAsString("category_id"))
                assertThat(c.getString(1)).isEqualTo(it.getAsString("category_name"))
            }
        }
    }

    @Test
    fun insert() {
        // setup
        val values = twitchChannelStreamSchedule14(99, "cid0", "3")
        // exercise
        val actual =
            newDb.insert("twitch_channel_schedule_stream", SQLiteDatabase.CONFLICT_ABORT, values)
        // verify
        assertThat(actual).isGreaterThan(-1)
    }

    @Test
    fun insert_throwConstraintExceptionForCategoryId() = runTest {
        // setup
        val values = twitchChannelStreamSchedule14(99, "cid99", "3")
        // exercise
        assertThatThrownBy {
            newDb.insert("twitch_channel_schedule_stream", SQLiteDatabase.CONFLICT_ABORT, values)
            // verify
        }.isInstanceOf(SQLiteConstraintException::class.java)
    }

    @Test
    fun migrateWithAppDatabase_throwConstraintExceptionForUserId() = runTest {
        // setup
        val values = twitchChannelStreamSchedule14(99, "cid1", "99")
        // exercise
        assertThatThrownBy {
            newDb.insert("twitch_channel_schedule_stream", SQLiteDatabase.CONFLICT_ABORT, values)
            // verify
        }.isInstanceOf(SQLiteConstraintException::class.java)
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
