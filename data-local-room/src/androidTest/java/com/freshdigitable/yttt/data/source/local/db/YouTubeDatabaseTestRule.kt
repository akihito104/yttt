package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

internal class YouTubeDatabaseTestRule() :
    DataSourceTestRule<YouTubeDao, YouTubeLocalDataSource>() {
    override fun createDao(database: AppDatabase): YouTubeDao = YouTubeDao(
        database,
        videoDao = YouTubeVideoDaoImpl(database),
        channelDao = YouTubeChannelDaoImpl(database),
        playlistDao = YouTubePlaylistDaoImpl(database),
        subscriptionDao = YouTubeSubscriptionDaoImpl(database),
    )

    override fun createTestScope(testScope: TestScope): DatabaseTestScope<YouTubeDao, YouTubeLocalDataSource> =
        YouTubeDatabaseTestScope(
            testScope = testScope,
            dao = dao,
            dataSource = YouTubeLocalDataSource(
                database,
                dao,
                NopImageDataSource,
                IoScope(StandardTestDispatcher(testScope.testScheduler)),
            ),
        )

    internal class YouTubeDatabaseTestScope(
        override val testScope: TestScope,
        override val dao: YouTubeDao,
        override val dataSource: YouTubeLocalDataSource,
    ) : DatabaseTestScope<YouTubeDao, YouTubeLocalDataSource>
}
