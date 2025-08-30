package com.freshdigitable.yttt.data.source.local.db

import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource

internal class YouTubeDatabaseTestRule : DataSourceTestRule<YouTubeDao, YouTubeLocalDataSource>() {
    override fun createDao(database: AppDatabase): YouTubeDao = YouTubeDao(
        database,
        videoDao = YouTubeVideoDaoImpl(database),
        channelDao = YouTubeChannelDaoImpl(database),
        playlistDao = YouTubePlaylistDaoImpl(database),
        subscriptionDao = YouTubeSubscriptionDaoImpl(database),
    )

    override fun createLocalSource(ioScope: IoScope): YouTubeLocalDataSource =
        YouTubeLocalDataSource(database, dao, NopImageDataSource, ioScope)
}
