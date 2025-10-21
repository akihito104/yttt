package com.freshdigitable.yttt.data.source.local.fixture

import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.LiveLocalPagingSource
import com.freshdigitable.yttt.data.source.local.db.LiveDao
import com.freshdigitable.yttt.data.source.local.db.LiveVideoItemDaoImpl
import com.freshdigitable.yttt.data.source.local.fixture.TwitchDataSourceTestRule.TwitchDataSourceScope
import com.freshdigitable.yttt.data.source.local.fixture.YouTubeDatabaseTestRule.YouTubeDataSourceScope

internal class LiveDataSourceTestRule : DataSourceTestRule<LiveDataSourceTestRule.LiveDataSourceScope>() {
    override fun createTestScope(ioScope: IoScope): LiveDataSourceScope = LiveDataSourceScope(ioScope, database)

    internal class LiveDataSourceScope(
        ioScope: IoScope,
        database: AppDatabase,
        youtubeSource: YouTubeDataSourceScope = YouTubeDataSourceScope(ioScope, database),
        twitchSource: TwitchDataSourceScope = TwitchDataSourceScope(ioScope, database),
    ) : DataSourceScope {
        val dao = LiveDao(LiveVideoItemDaoImpl(database))
        val pagingSource = LiveLocalPagingSource(LiveDao(LiveVideoItemDaoImpl(database)))
        val youtube = youtubeSource
        val twitch = twitchSource
    }
}
