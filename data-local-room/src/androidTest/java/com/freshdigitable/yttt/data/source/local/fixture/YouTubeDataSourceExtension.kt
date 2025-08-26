package com.freshdigitable.yttt.data.source.local.fixture

import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.YouTubeLocalDataSource
import com.freshdigitable.yttt.data.source.local.db.YouTubeChannelDaoImpl
import com.freshdigitable.yttt.data.source.local.db.YouTubeDao
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistDaoImpl
import com.freshdigitable.yttt.data.source.local.db.YouTubeSubscriptionDaoImpl
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoDaoImpl
import com.freshdigitable.yttt.data.source.local.fixture.DatabaseExtension.Companion.KEY_DB
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.reflect.KClass

internal typealias YouTubeDataSourceTestScope = DataSourceTestScope<YouTubeDao, YouTubeLocalDataSource>

internal class YouTubeDataSourceExtension : DataSourceTestScopeExtension {
    override val params: Map<KClass<*>, Pair<String, (ExtensionContext) -> Any>> = mapOf(
        YouTubeDao::class to ("dao" to { it.dao() }),
        DataSourceTestScope::class to ("datastore" to { it.testScope() }),
    )

    companion object {
        fun ExtensionContext.dao(database: AppDatabase? = null): YouTubeDao {
            val db = database ?: storeParTestMethod.get(KEY_DB, AppDatabase::class.java)
            return YouTubeDao(
                db,
                videoDao = YouTubeVideoDaoImpl(db),
                channelDao = YouTubeChannelDaoImpl(db),
                playlistDao = YouTubePlaylistDaoImpl(db),
                subscriptionDao = YouTubeSubscriptionDaoImpl(db),
            )
        }

        fun ExtensionContext.testScope(): YouTubeDataSourceTestScope {
            val database = storeParTestMethod.get(KEY_DB, AppDatabase::class.java)
            val dao = dao(database)
            return DataSourceTestScope(
                database = database,
                dao = dao,
                factory = {
                    val ioScope = IoScope(StandardTestDispatcher(it.testScheduler))
                    YouTubeLocalDataSource(database, dao, NopImageDataSource, ioScope)
                },
            )
        }
    }
}
