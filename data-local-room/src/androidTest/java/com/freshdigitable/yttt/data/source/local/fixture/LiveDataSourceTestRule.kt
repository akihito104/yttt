package com.freshdigitable.yttt.data.source.local.fixture

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.LiveLocalPagingSource
import com.freshdigitable.yttt.data.source.local.db.LiveDao
import com.freshdigitable.yttt.data.source.local.db.LiveTimelineItemDaoImpl
import com.freshdigitable.yttt.data.source.local.fixture.TwitchDataSourceTestRule.TwitchDataSourceScope
import com.freshdigitable.yttt.data.source.local.fixture.YouTubeDatabaseTestRule.YouTubeDataSourceScope
import java.time.Instant

internal class LiveDataSourceTestRule : DataSourceTestRule<LiveDataSourceTestRule.LiveDataSourceScope>() {
    override fun createTestScope(ioScope: IoScope): LiveDataSourceScope = LiveDataSourceScope(ioScope, database)

    internal class LiveDataSourceScope(
        ioScope: IoScope,
        database: AppDatabase,
        youtubeSource: YouTubeDataSourceScope = YouTubeDataSourceScope(ioScope, database),
        twitchSource: TwitchDataSourceScope = TwitchDataSourceScope(ioScope, database),
    ) : DataSourceScope {
        var current: Instant = Instant.EPOCH
        val dao = LiveDao(LiveTimelineItemDaoImpl(database))
        val pagingSource = LiveLocalPagingSource(
            LiveDao(LiveTimelineItemDaoImpl(database)),
            object : DateTimeProvider {
                override fun now(): Instant = current
            },
        )
        val youtube = youtubeSource
        val twitch = twitchSource
    }
}
