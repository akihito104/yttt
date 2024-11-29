package com.freshdigitable.yttt.data.source.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.freshdigitable.yttt.data.source.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description

internal class DatabaseTestRule : TestWatcher() {
    private lateinit var database: AppDatabase
    private lateinit var dao: YouTubeDao

    inline fun runWithDao(
        crossinline body: suspend AppDatabase.(YouTubeDao) -> Unit
    ) = runBlocking { database.body(dao) }

    override fun starting(description: Description?) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = YouTubeDao(
            database,
            videoDao = YouTubeVideoDaoImpl(database),
            channelDao = YouTubeChannelDaoImpl(database),
            playlistDao = YouTubePlaylistDaoImpl(database),
            subscriptionDao = YouTubeSubscriptionDaoImpl(database),
        )
    }

    override fun finished(description: Description?) {
        database.close()
    }
}
