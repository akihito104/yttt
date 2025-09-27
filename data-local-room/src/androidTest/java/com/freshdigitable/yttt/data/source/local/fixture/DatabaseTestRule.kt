package com.freshdigitable.yttt.data.source.local.fixture

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.room.util.useCursor
import androidx.test.core.app.ApplicationProvider
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal class DatabaseTestRule : TestWatcher() {
    internal lateinit var database: AppDatabase
        private set

    override fun starting(description: Description?) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    override fun finished(description: Description?) {
        database.close()
    }
}

internal abstract class DataSourceTestRule<Dao, Local, Extended> : TestWatcher() {
    private val databaseRule = DatabaseTestRule()
    internal val database: AppDatabase get() = databaseRule.database
    private var _dao: Dao? = null
    protected val dao: Dao get() = checkNotNull(_dao)
    fun runWithDao(body: suspend CoroutineScope.(Dao) -> Unit) = runTest { body(dao) }
    fun runWithLocalSource(body: suspend DatabaseTestScope<Dao, Local, Extended>.() -> Unit) = runTest {
        val ioScope = IoScope(StandardTestDispatcher(testScheduler))
        val scope = createTestScope(ioScope)
        scope.body()
    }

    abstract fun createDao(database: AppDatabase): Dao
    abstract fun createTestScope(ioScope: IoScope): DatabaseTestScope<Dao, Local, Extended>

    override fun starting(description: Description?) {
        _dao = createDao(database)
    }

    override fun finished(description: Description?) {
        _dao = null
    }

    override fun apply(base: Statement?, description: Description?): Statement =
        RuleChain.outerRule(databaseRule)
            .apply(super.apply(base, description), description)

    fun <E> query(stmt: String, res: (Cursor) -> E): E = database.query(stmt, null).useCursor(res)

    internal class DatabaseTestScope<Dao, Local, Extended>(
        val dao: Dao,
        val localSource: Local,
        val extendedSource: Extended,
    )
}

internal object NopImageDataSource : ImageDataSource {
    override fun removeImageByUrl(url: Collection<String>) {}
}
