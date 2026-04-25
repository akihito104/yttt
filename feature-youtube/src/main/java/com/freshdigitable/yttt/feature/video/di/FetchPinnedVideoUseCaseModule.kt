package com.freshdigitable.yttt.feature.video.di

import com.freshdigitable.yttt.feature.video.FetchPinnedVideoFromYouTubeUseCase
import com.freshdigitable.yttt.feature.video.FetchPinnedVideoUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface FetchPinnedVideoUseCaseModule {
    @Binds
    fun bindFetchPinnedVideoUseCase(useCase: FetchPinnedVideoFromYouTubeUseCase): FetchPinnedVideoUseCase
}
