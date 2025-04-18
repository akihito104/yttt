package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import java.time.Instant

internal class YouTubeDatabaseTestRule(
    baseTime: Instant = Instant.EPOCH,
) : DataSourceTestRule<YouTubeDao, YouTubeLocalDataSource>(baseTime) {
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
            dateTimeProvider = dateTimeProvider,
            dao = dao,
            dataSource = YouTubeLocalDataSource(
                database,
                dao,
                NopImageDataSource,
                dateTimeProvider,
                IoScope(StandardTestDispatcher(testScope.testScheduler))
            ),
        )

    internal class YouTubeDatabaseTestScope(
        override val testScope: TestScope,
        override val dateTimeProvider: DateTimeProviderFake,
        override val dao: YouTubeDao,
        override val dataSource: YouTubeLocalDataSource,
    ) : DatabaseTestScope<YouTubeDao, YouTubeLocalDataSource>
}
