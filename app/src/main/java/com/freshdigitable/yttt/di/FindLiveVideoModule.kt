package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.FindLiveVideoFromTwitchUseCase
import com.freshdigitable.yttt.FindLiveVideoFromYouTubeUseCase
import com.freshdigitable.yttt.FindLiveVideoUseCase
import com.freshdigitable.yttt.data.model.LivePlatform
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@MapKey
annotation class PlatformKey(val platform: LivePlatform)

@Suppress("unused")
@Module
@InstallIn(ViewModelComponent::class)
interface FindLiveVideoModule {
    @Binds
    @IntoMap
    @PlatformKey(LivePlatform.TWITCH)
    fun bindFindLiveVideoFromTwitchUseCase(useCase: FindLiveVideoFromTwitchUseCase): FindLiveVideoUseCase

    @Binds
    @IntoMap
    @PlatformKey(LivePlatform.YOUTUBE)
    fun bindFindLiveVideoFromYouTubeUseCase(useCase: FindLiveVideoFromYouTubeUseCase): FindLiveVideoUseCase
}
