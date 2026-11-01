package com.freshdigitable.yttt.feature.video.di

import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.feature.video.FindLiveVideoDetailFromTwitchUseCase
import com.freshdigitable.yttt.feature.video.FindLiveVideoDetailUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@Suppress("unused")
@Module
@InstallIn(ViewModelComponent::class)
internal interface TwitchVideoModule {
    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchStream.Id::class)
    fun bindFindLiveVideoFromTwitchUseCase(useCase: FindLiveVideoDetailFromTwitchUseCase): FindLiveVideoDetailUseCase

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchChannelSchedule.Stream.Id::class)
    fun bindFindLiveVideoFromTwitchUseCaseForStreamScheduleId(
        useCase: FindLiveVideoDetailFromTwitchUseCase,
    ): FindLiveVideoDetailUseCase
}
