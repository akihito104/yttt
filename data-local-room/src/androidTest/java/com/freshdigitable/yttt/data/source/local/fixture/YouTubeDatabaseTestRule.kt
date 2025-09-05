package com.freshdigitable.yttt.data.source.local.fixture

import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelDaoImpl
import com.freshdigitable.yttt.data.source.local.db.YouTubeDao
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistDaoImpl
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionDaoImpl
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoDaoImpl

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
