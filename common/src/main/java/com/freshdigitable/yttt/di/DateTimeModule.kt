package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.DateTimeProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DateTimeModule {
    @Binds
    @Singleton
    fun bindDateTimeProvider(provider: DateTimeProviderImpl): DateTimeProvider
}
