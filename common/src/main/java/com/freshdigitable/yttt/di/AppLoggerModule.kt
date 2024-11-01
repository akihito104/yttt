package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.AppLoggerSetup
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppLoggerModule {
    companion object {
        @Provides
        @Singleton
        @IntoSet
        fun provideAppLoggerSetup(): AppLoggerSetup = {}
    }
}
