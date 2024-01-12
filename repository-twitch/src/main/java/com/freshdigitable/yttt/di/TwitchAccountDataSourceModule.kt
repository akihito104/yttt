package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.source.TwitchAccountDataStore
import com.freshdigitable.yttt.data.source.local.TwitchAccountLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface TwitchAccountDataSourceModule {
    @Binds
    fun bindTwitchAccountLocalDataSource(source: TwitchAccountLocalDataSource): TwitchAccountDataStore.Local
}
