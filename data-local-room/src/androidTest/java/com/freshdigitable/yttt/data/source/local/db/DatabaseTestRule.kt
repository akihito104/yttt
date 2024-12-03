package com.freshdigitable.yttt.data.source.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.rules.TestWatcher
import org.junit.runner.Description

internal class DatabaseTestRule : TestWatcher() {
    private lateinit var database: AppDatabase
    private lateinit var dao: YouTubeDao

    fun runWithDao(body: suspend CoroutineScope.(YouTubeDao) -> Unit) =
        runTest { body(dao) }

    fun runWithLocalSource(
        datetimeProvider: DateTimeProvider,
        body: suspend CoroutineScope.(YouTubeDao, YouTubeLocalDataSource) -> Unit,
    ) = runTest { body(dao, localSource(datetimeProvider, StandardTestDispatcher(testScheduler))) }

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

    companion object {
        private fun DatabaseTestRule.localSource(
            datetimeProvider: DateTimeProvider,
            ioDispatcher: CoroutineDispatcher,
        ): YouTubeLocalDataSource =
            YouTubeLocalDataSource(database, dao, datetimeProvider, ioDispatcher)
    }
}
