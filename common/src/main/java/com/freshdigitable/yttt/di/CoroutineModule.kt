package com.freshdigitable.yttt.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface CoroutineModule {
    companion object {
        @Provides
        @Singleton
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Singleton
        @Provides
        fun provideIoCoroutineScope(): CoroutineScope =
            CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}
