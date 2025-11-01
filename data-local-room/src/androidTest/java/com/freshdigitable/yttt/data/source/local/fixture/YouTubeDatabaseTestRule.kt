package com.freshdigitable.yttt.data.source.local.fixture

import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.YouTubeChannelLocalDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeExtendedDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource
import com.freshdigitable.yttt.data.source.local.YouTubePlaylistLocalDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeSubscriptionLocalDataSource
import com.freshdigitable.yttt.data.source.local.YouTubeVideoLocalDataSource
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelDaoImpl
import com.freshdigitable.yttt.data.source.local.db.YouTubeDao
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistDaoImpl
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionDaoImpl
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoDaoImpl

internal class YouTubeDatabaseTestRule : DataSourceTestRule<YouTubeDatabaseTestRule.YouTubeDataSourceScope>() {
    override fun createTestScope(ioScope: IoScope): YouTubeDataSourceScope = YouTubeDataSourceScope(ioScope, database)

    class YouTubeDataSourceScope(val ioScope: IoScope, val database: AppDatabase) : DataSourceScope {
        val dao = YouTubeDao(
            database,
            videoDao = YouTubeVideoDaoImpl(database),
            channelDao = YouTubeChannelDaoImpl(database),
            playlistDao = YouTubePlaylistDaoImpl(database),
            subscriptionDao = YouTubeSubscriptionDaoImpl(database),
        )
        private val videoDataSource = YouTubeVideoLocalDataSource(
            database = database,
            dao = dao,
            imageDataSource = NopImageDataSource,
            ioScope = ioScope,
        )
        private val subscriptionDataSource = YouTubeSubscriptionLocalDataSource(
            database = database,
            dao = dao,
            ioScope = ioScope,
        )
        private val playlistDataSource = YouTubePlaylistLocalDataSource(
            dao = dao,
            ioScope = ioScope,
        )
        val localSource = YouTubeLocalDataSource(
            dao = dao,
            channelDataSource = YouTubeChannelLocalDataSource(
                database = database,
                dao = dao,
                ioScope = ioScope,
            ),
            videoDataSource = videoDataSource,
            subscriptionDataSource = subscriptionDataSource,
            playlistDataSource = playlistDataSource,
        )
        val extendedSource = YouTubeExtendedDataSource(
            database = database,
            dao = dao,
            videoDataSource = videoDataSource,
            subscriptionDataSource = subscriptionDataSource,
            playlistDataSource = playlistDataSource,
            ioScope = ioScope,
        )
    }
}
