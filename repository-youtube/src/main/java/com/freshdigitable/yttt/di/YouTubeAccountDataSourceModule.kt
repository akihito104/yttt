package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.YouTubeAccountRepository
import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.data.source.local.YouTubeAccountLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface YouTubeAccountDataSourceModule {
    @Binds
    fun bindYouTubeAccountLocalDataSource(source: YouTubeAccountLocalDataSource): YouTubeAccountDataStore.Local

    @Singleton
    @Binds
    @IntoMap
    @LivePlatformKey(YouTube::class)
    fun bindAccountRepository(repository: YouTubeAccountRepository): AccountRepository
}
