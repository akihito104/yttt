package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.FindLiveVideoFromTwitchUseCase
import com.freshdigitable.yttt.FindLiveVideoFromYouTubeUseCase
import com.freshdigitable.yttt.FindLiveVideoUseCase
import com.freshdigitable.yttt.data.model.IdBase
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.YouTubeVideo
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap
import kotlin.reflect.KClass

@MapKey
annotation class IdBaseClassKey(val value: KClass<out IdBase>)

typealias IdBaseClassMap<T> = Map<Class<out IdBase>, @JvmSuppressWildcards T>

@Suppress("unused")
@Module
@InstallIn(ViewModelComponent::class)
interface FindLiveVideoModule {
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
    @IdBaseClassKey(YouTubeVideo.Id::class)
    fun bindFindLiveVideoFromYouTubeUseCase(useCase: FindLiveVideoFromYouTubeUseCase): FindLiveVideoUseCase
}