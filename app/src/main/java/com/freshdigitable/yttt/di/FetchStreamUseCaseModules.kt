package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.FetchStreamUseCase
import com.freshdigitable.yttt.FetchTwitchStreamUseCase
import com.freshdigitable.yttt.FetchYouTubeStreamUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoSet

@Suppress("unused")
@InstallIn(ViewModelComponent::class)
@Module
interface FetchStreamUseCaseModules {
    @Binds
    @IntoSet
    fun bindYouTubeStreamUseCase(useCase: FetchYouTubeStreamUseCase): FetchStreamUseCase

    @Binds
    @IntoSet
    fun bindTwitchStreamUseCase(useCase: FetchTwitchStreamUseCase): FetchStreamUseCase
}
