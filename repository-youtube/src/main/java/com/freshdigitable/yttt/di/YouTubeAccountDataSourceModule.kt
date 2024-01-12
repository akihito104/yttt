package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.data.source.local.YouTubeAccountLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface YouTubeAccountDataSourceModule {
    @Binds
    fun bindYouTubeAccountLocalDataSource(source: YouTubeAccountLocalDataSource): YouTubeAccountDataStore.Local
}
