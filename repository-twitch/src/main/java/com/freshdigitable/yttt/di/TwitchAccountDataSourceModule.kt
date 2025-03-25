package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.TwitchAccountRepository
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.TwitchAccountDataStore
import com.freshdigitable.yttt.data.source.local.TwitchAccountLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface TwitchAccountDataSourceModule {
    @Binds
    fun bindTwitchAccountLocalDataSource(source: TwitchAccountLocalDataSource): TwitchAccountDataStore.Local

    @Singleton
    @Binds
    @IntoMap
    @LivePlatformKey(Twitch::class)
    fun bindAccountRepository(repository: TwitchAccountRepository): AccountRepository
}
