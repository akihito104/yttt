package com.freshdigitable.yttt.test

import android.content.Context
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.source.local.AppDatabase
import com.freshdigitable.yttt.data.source.local.di.DbFlagModule
import com.freshdigitable.yttt.data.source.local.di.DbModule
import com.freshdigitable.yttt.data.source.local.di.createInMemory
import com.freshdigitable.yttt.di.ClassMap
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.jetbrains.annotations.TestOnly
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import javax.inject.Singleton

@TestOnly
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DbFlagModule::class],
)
interface InMemoryDbModule {
    companion object {
        @Provides
        @Singleton
        internal fun provideFlag(): DbFlagModule.Flag = DbFlagModule.Flag.InMemory
    }
}

@TestOnly
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DbModule::class],
)
interface InMemoryDatabaseModule {
    companion object {
        internal var db: AppDatabase? = null

        @Provides
        @Singleton
        internal fun provideDatabase(
            @ApplicationContext context: Context,
            platforms: ClassMap<LivePlatform, LivePlatform>,
        ): AppDatabase {
            if (this.db?.isOpen == true) {
                return requireNotNull(db)
            }
            val db = AppDatabase.createInMemory(context, platforms.values)
            this.db = db
            return db
        }
    }
}

class InMemoryDbRule : TestWatcher() {
    override fun finished(description: Description?) {
        super.finished(description)
        InMemoryDatabaseModule.db?.close()
        InMemoryDatabaseModule.db = null
    }
}
