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

internal class YouTubeDatabaseTestRule : DataSourceTestRule<YouTubeDao, YouTubeLocalDataSource>() {
    override fun createDao(database: AppDatabase): YouTubeDao = YouTubeDao(
        database,
        videoDao = YouTubeVideoDaoImpl(database),
        channelDao = YouTubeChannelDaoImpl(database),
        playlistDao = YouTubePlaylistDaoImpl(database),
        subscriptionDao = YouTubeSubscriptionDaoImpl(database),
    )

    override fun createLocalSource(ioScope: IoScope): YouTubeLocalDataSource {
        val videoDataSource = YouTubeVideoLocalDataSource(
            database = database,
            dao = dao,
            imageDataSource = NopImageDataSource,
            ioScope = ioScope,
        )
        val subscriptionDataSource = YouTubeSubscriptionLocalDataSource(
            database = database,
            dao = dao,
            ioScope = ioScope,
        )
        val playlistDataSource = YouTubePlaylistLocalDataSource(
            dao = dao,
            ioScope = ioScope,
        )
        return YouTubeLocalDataSource(
            dao = dao,
            channelDataSource = YouTubeChannelLocalDataSource(
                database = database,
                dao = dao,
                ioScope = ioScope,
            ),
            videoDataSource = videoDataSource,
            subscriptionDataSource = subscriptionDataSource,
            playlistDataSource = playlistDataSource,
            extendedDataSource = YouTubeExtendedDataSource(
                database = database,
                dao = dao,
                videoDataSource = videoDataSource,
                subscriptionDataSource = subscriptionDataSource,
                playlistDataSource = playlistDataSource,
            ),
        )
    }
}
