package com.freshdigitable.yttt.data.source.local.db

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.room.util.useCursor
import androidx.test.core.app.ApplicationProvider
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.source.ImageDataSource
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

    fun <E> query(stmt: String, res: (Cursor) -> E): E = database.query(stmt, null).useCursor(res)

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
        ): YouTubeLocalDataSource = YouTubeLocalDataSource(
            database,
            dao,
            NopImageDataSource,
            datetimeProvider,
            ioDispatcher,
        )
    }
}

private object NopImageDataSource : ImageDataSource {
    override fun removeImageByUrl(url: Collection<String>) {}
}
