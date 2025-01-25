package com.freshdigitable.yttt.data.source.local.db

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.room.util.useCursor
import androidx.test.core.app.ApplicationProvider
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.CoroutineContext

internal abstract class DatabaseTestRule<Dao, Source>(baseTime: Instant) : TestWatcher() {
    protected lateinit var database: AppDatabase
        private set
    private var _dao: Dao? = null
    protected val dao: Dao get() = checkNotNull(_dao)
    val dateTimeProvider: DateTimeProviderFake = DateTimeProviderFake(baseTime)
    fun runWithDao(body: suspend CoroutineScope.(Dao) -> Unit) = runTest { body(dao) }
    fun runWithLocalSource(body: suspend DatabaseTestScope<Dao, Source>.() -> Unit) = runTest {
        createTestScope(this).body()
    }

    abstract fun createDao(database: AppDatabase): Dao
    abstract fun createTestScope(testScope: TestScope): DatabaseTestScope<Dao, Source>

    override fun starting(description: Description?) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        _dao = createDao(database)
    }

    override fun finished(description: Description?) {
        database.close()
        _dao = null
    }

    fun <E> query(stmt: String, res: (Cursor) -> E): E = database.query(stmt, null).useCursor(res)

    internal interface DatabaseTestScope<Dao, Source> : CoroutineScope {
        val testScope: TestScope
        val dateTimeProvider: DateTimeProviderFake
        val dao: Dao
        val dataSource: Source
        override val coroutineContext: CoroutineContext
            get() = testScope.coroutineContext
    }
}

internal object NopImageDataSource : ImageDataSource {
    override fun removeImageByUrl(url: Collection<String>) {}
}

internal class DateTimeProviderFake(value: Instant = Instant.EPOCH) : DateTimeProvider {
    private var _value = value
    fun setValue(value: Instant) {
        _value = value
    }

    fun advance(value: Duration) {
        _value += value
    }

    override fun now(): Instant = _value
}
