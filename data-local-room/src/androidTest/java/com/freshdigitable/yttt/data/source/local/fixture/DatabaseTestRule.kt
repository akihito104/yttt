package com.freshdigitable.yttt.data.source.local.fixture

import android.content.Context
import androidx.room.Room
import androidx.room.util.useCursor
import androidx.test.core.app.ApplicationProvider
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.db.LivePlatformConverter
import com.freshdigitable.yttt.data.source.local.db.YouTubeVideoTable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.model.Statement

internal class DatabaseTestRule : TestWatcher() {
    internal var database: AppDatabase? = null
        private set

    override fun starting(description: Description?) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addTypeConverter(LivePlatformConverter(setOf(Twitch, YouTube)))
            .build()
    }

    override fun finished(description: Description?) {
        database?.close()
        database = null
    }
}

internal abstract class DataSourceTestRule<T : DataSourceTestRule.DataSourceScope> : TestWatcher() {
    private val databaseRule = DatabaseTestRule()
    internal val database: AppDatabase get() = checkNotNull(databaseRule.database)
    fun runWithScope(body: suspend T.() -> Unit) = runTest {
        val ioScope = IoScope(StandardTestDispatcher(testScheduler))
        val scope = createTestScope(ioScope)
        scope.body()
    }

    abstract fun createTestScope(ioScope: IoScope): T

    override fun apply(base: Statement?, description: Description?): Statement =
        RuleChain.outerRule(databaseRule)
            .apply(super.apply(base, description), description)

    internal interface DataSourceScope
}

internal fun AppDatabase.findAllArchivedVideos(): List<YouTubeVideo.Id> =
    query(YouTubeVideoTable.Dao.SQL_FIND_ALL_ARCHIVED_VIDEOS, null).useCursor {
        buildList {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndex("id"))
                add(YouTubeVideo.Id(id))
            }
        }
    }

internal object NopImageDataSource : ImageDataSource {
    override fun removeImageByUrl(url: Collection<String>) {}
}
