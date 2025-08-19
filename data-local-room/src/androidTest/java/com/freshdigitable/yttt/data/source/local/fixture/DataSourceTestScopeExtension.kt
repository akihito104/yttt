package com.freshdigitable.yttt.data.source.local.fixture

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.room.util.useCursor
import androidx.test.core.app.ApplicationProvider
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.local.AppDatabase
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import kotlin.reflect.KClass

internal val ExtensionContext.storeParTestMethod: ExtensionContext.Store
    get() = getStore(ExtensionContext.Namespace.create(requiredTestMethod))

internal class DatabaseExtension : BeforeEachCallback, AfterEachCallback, ParameterResolver {
    companion object {
        const val KEY_DB = "database"
        private fun appDatabase(): AppDatabase {
            val c = ApplicationProvider.getApplicationContext<Context>()
            return Room.inMemoryDatabaseBuilder(c, AppDatabase::class.java).build()
        }
    }

    override fun beforeEach(context: ExtensionContext) {
        context.storeParTestMethod.put(KEY_DB, appDatabase())
    }

    override fun afterEach(context: ExtensionContext) {
        (context.storeParTestMethod.remove(KEY_DB) as? AppDatabase)?.close()
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean = parameterContext.parameter.type == AppDatabase::class.java

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any? = when (parameterContext.parameter.type) {
        AppDatabase::class.java -> extensionContext.storeParTestMethod.getOrComputeIfAbsent(KEY_DB) { appDatabase() }
        else -> throw IllegalArgumentException("Unsupported parameter type: ${parameterContext.parameter.type}")
    }
}

internal interface DataSourceTestScopeExtension : ParameterResolver {
    val params: Map<KClass<*>, Pair<String, (ExtensionContext) -> Any>>
    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean = params.keys.map { it.java }.contains(parameterContext.parameter.type)

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any? {
        val store = extensionContext.storeParTestMethod
        val (key, p) = params.entries.first { it.key.java == parameterContext.parameter.type }.value
        return store.getOrComputeIfAbsent(key) { p(extensionContext) }
    }
}

internal class DataSourceTestScope<Dao, DataStore>(
    val database: AppDatabase,
    val dao: Dao,
    private val factory: (TestScope) -> DataStore,
) {
    private var _dataSource: DataStore? = null
    val dataSource: DataStore get() = checkNotNull(_dataSource)
    fun scopedTest(block: suspend TestScope.() -> Unit) = runTest {
        _dataSource = factory(this)
        block()
    }

    fun <E> query(stmt: String, res: (Cursor) -> E): E =
        database.query(stmt, null).useCursor(res)
}

internal object NopImageDataSource : ImageDataSource {
    override fun removeImageByUrl(url: Collection<String>) {}
}
