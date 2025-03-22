package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.model.DateTimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DateTimeModule {
    companion object {
        @Provides
        @Singleton
        fun provideDateTimeProvider(): DateTimeProvider = DateTimeProviderImpl()
    }
}

internal class DateTimeProviderImpl : DateTimeProvider {
    override fun now(): Instant = Instant.now()
}
