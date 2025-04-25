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
import javax.inject.Singleton

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
        fun provideIoCoroutineScope(): CoroutineScope =
            CoroutineScope(StandardTestDispatcher(testScheduler))

        @Provides
        @Singleton
        fun provideIoDispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)
    }
}
