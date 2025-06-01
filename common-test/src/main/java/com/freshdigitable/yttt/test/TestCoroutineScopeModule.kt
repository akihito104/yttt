package com.freshdigitable.yttt.test

import com.freshdigitable.yttt.di.CoroutineModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import javax.inject.Singleton
import kotlin.time.Duration

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [CoroutineModule::class],
)
interface TestCoroutineScopeModule {
    companion object {
        var testScheduler: TestCoroutineScheduler? = null

        @Provides
        @Singleton
        fun provideIoCoroutineScope(coroutineDispatcher: CoroutineDispatcher): CoroutineScope =
            CoroutineScope(coroutineDispatcher)

        @Provides
        @Singleton
        fun provideIoDispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler!!)

        fun clean() {
            testScheduler = null
        }
    }
}

class TestCoroutineScopeRule : TestWatcher() {
    private val testScope = TestScope()
    fun runTest(timeout: Duration? = null, testBody: suspend TestScope.() -> Unit) {
        if (timeout == null) {
            testScope.runTest(testBody = testBody)
        } else {
            testScope.runTest(timeout = timeout, testBody = testBody)
        }
    }

    override fun starting(description: Description) {
        TestCoroutineScopeModule.testScheduler = testScope.testScheduler
    }

    override fun finished(description: Description?) {
        TestCoroutineScopeModule.clean()
    }
}
