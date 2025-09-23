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

internal class TwitchDataSourceTestRule :
    DataSourceTestRule<TwitchDao, TwitchLocalDataSource, TwitchExtendedDataSource>() {
    override fun createDao(database: AppDatabase): TwitchDao = TwitchDao(
        database,
        TwitchUserDaoImpl(database),
        TwitchScheduleDaoImpl(database),
        TwitchStreamDaoImpl(database),
    )

    override fun createTestScope(
        ioScope: IoScope,
    ): DatabaseTestScope<TwitchDao, TwitchLocalDataSource, TwitchExtendedDataSource> {
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
        return DatabaseTestScope(
            dao = dao,
            localSource = TwitchLocalDataSource(
                dao = dao,
                ioScope = ioScope,
                userDataSource = userDataSource,
                streamDataSource = streamDataSource,
                scheduleDataSource = scheduleDataSource,
            ),
            extendedSource = TwitchExtendedDataSource(
                dao = dao,
                userDataSource = userDataSource,
                streamDataSource = streamDataSource,
                scheduleDataSource = scheduleDataSource,
            ),
        )
    }
}
