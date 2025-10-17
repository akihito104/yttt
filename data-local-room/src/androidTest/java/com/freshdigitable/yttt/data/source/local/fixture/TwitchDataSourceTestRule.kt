package com.freshdigitable.yttt.data.source.local.fixture

import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.TwitchExtendedDataSource
import com.freshdigitable.yttt.data.source.local.TwitchLocalDataSource
import com.freshdigitable.yttt.data.source.local.TwitchScheduleLocalDataSource
import com.freshdigitable.yttt.data.source.local.TwitchStreamLocalDataSource
import com.freshdigitable.yttt.data.source.local.TwitchUserLocalDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import com.freshdigitable.yttt.data.source.local.db.TwitchScheduleDaoImpl
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamDaoImpl
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDaoImpl

internal class TwitchDataSourceTestRule : DataSourceTestRule<TwitchDataSourceTestRule.TwitchDataSourceScope>() {
    override fun createTestScope(ioScope: IoScope): TwitchDataSourceScope = TwitchDataSourceScope(ioScope, database)

    class TwitchDataSourceScope(ioScope: IoScope, database: AppDatabase) : DataSourceScope {
        val dao = TwitchDao(
            database,
            TwitchUserDaoImpl(database),
            TwitchScheduleDaoImpl(database),
            TwitchStreamDaoImpl(database),
        )
        val userDataSource = TwitchUserLocalDataSource(
            dao = dao,
            ioScope = ioScope,
        )
        val streamDataSource = TwitchStreamLocalDataSource(
            dao = dao,
            ioScope = ioScope,
            imageDataSource = NopImageDataSource,
        )
        val scheduleDataSource = TwitchScheduleLocalDataSource(
            dao = dao,
            ioScope = ioScope,
        )
        val localSource = TwitchLocalDataSource(
            dao = dao,
            ioScope = ioScope,
            userDataSource = userDataSource,
            streamDataSource = streamDataSource,
            scheduleDataSource = scheduleDataSource,
        )
        val extendedSource = TwitchExtendedDataSource(
            dao = dao,
            userDataSource = userDataSource,
            streamDataSource = streamDataSource,
            scheduleDataSource = scheduleDataSource,
        )
    }
}
