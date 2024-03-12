package com.freshdigitable.yttt.feature.video.di

import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.feature.video.FindLiveVideoDetailAnnotatedFromTwitchUseCase
import com.freshdigitable.yttt.feature.video.FindLiveVideoDetailAnnotatedUseCase
import com.freshdigitable.yttt.feature.video.FindLiveVideoFromTwitchUseCase
import com.freshdigitable.yttt.feature.video.FindLiveVideoUseCase
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
    fun bindFindLiveVideoFromTwitchUseCase(useCase: FindLiveVideoFromTwitchUseCase): FindLiveVideoUseCase

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchChannelSchedule.Stream.Id::class)
    fun bindFindLiveVideoFromTwitchUseCaseForStreamScheduleId(useCase: FindLiveVideoFromTwitchUseCase): FindLiveVideoUseCase

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchStream.Id::class)
    fun bindFindLiveVideoDetailAnnotationFromTwitchUseCase(useCase: FindLiveVideoDetailAnnotatedFromTwitchUseCase): FindLiveVideoDetailAnnotatedUseCase

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchChannelSchedule.Stream.Id::class)
    fun bindFindLiveVideoDetailAnnotationFromTwitchUseCaseForStreamScheduleId(useCase: FindLiveVideoDetailAnnotatedFromTwitchUseCase): FindLiveVideoDetailAnnotatedUseCase
}
