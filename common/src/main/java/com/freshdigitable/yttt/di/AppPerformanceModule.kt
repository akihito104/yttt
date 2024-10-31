package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.AppPerformance
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppPerformanceModule {
    companion object {
        @Provides
        @Singleton
        fun provideAppPerformance(): AppPerformance = AppPerformance.create()
    }
}
