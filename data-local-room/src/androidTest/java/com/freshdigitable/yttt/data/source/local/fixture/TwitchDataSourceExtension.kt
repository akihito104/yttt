package com.freshdigitable.yttt.data.source.local.fixture

import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.TwitchLocalDataSource
import com.freshdigitable.yttt.data.source.local.db.TwitchDao
import com.freshdigitable.yttt.data.source.local.db.TwitchScheduleDaoImpl
import com.freshdigitable.yttt.data.source.local.db.TwitchStreamDaoImpl
import com.freshdigitable.yttt.data.source.local.db.TwitchUserDaoImpl
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.reflect.KClass

internal typealias TwitchDataSourceTestScope = DataSourceTestScope<TwitchDao, TwitchLocalDataSource>

internal class TwitchDataSourceExtension : DataSourceTestScopeExtension {
    override val params: Map<KClass<*>, Pair<String, (ExtensionContext) -> Any>> = mapOf(
        TwitchDao::class to ("dao" to { it.dao() }),
        DataSourceTestScope::class to ("datastore" to { it.testScope() }),
    )

    companion object {
        fun ExtensionContext.dao(database: AppDatabase? = null): TwitchDao {
            val db = database
                ?: storeParTestMethod.get(DatabaseExtension.KEY_DB, AppDatabase::class.java)
            return TwitchDao(
                db,
                TwitchUserDaoImpl(db),
                TwitchScheduleDaoImpl(db),
                TwitchStreamDaoImpl(db),
            )
        }

        fun ExtensionContext.testScope(): TwitchDataSourceTestScope {
            val database = storeParTestMethod.get(DatabaseExtension.KEY_DB, AppDatabase::class.java)
            val dao = dao(database)
            return DataSourceTestScope(
                database = database,
                dao = dao,
                factory = {
                    val ioScope = IoScope(StandardTestDispatcher(it.testScheduler))
                    TwitchLocalDataSource(dao, ioScope, NopImageDataSource)
                },
            )
        }
    }
}
