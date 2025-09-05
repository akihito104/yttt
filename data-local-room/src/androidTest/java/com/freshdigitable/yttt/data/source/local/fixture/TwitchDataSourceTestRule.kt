package com.freshdigitable.yttt.data.source.local.fixture

import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.TwitchLocalDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import com.freshdigitable.yttt.data.source.local.db.TwitchScheduleDaoImpl
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamDaoImpl
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDaoImpl

internal class TwitchDataSourceTestRule : DataSourceTestRule<TwitchDao, TwitchLocalDataSource>() {
    override fun createDao(database: AppDatabase): TwitchDao = TwitchDao(
        database,
        TwitchUserDaoImpl(database),
        TwitchScheduleDaoImpl(database),
        TwitchStreamDaoImpl(database),
    )

    override fun createLocalSource(ioScope: IoScope): TwitchLocalDataSource =
        TwitchLocalDataSource(dao, ioScope, NopImageDataSource)
}
