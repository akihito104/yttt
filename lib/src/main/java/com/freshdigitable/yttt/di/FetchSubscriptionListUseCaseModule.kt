package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.FetchSubscriptionListSourceFromTwitchUseCase
import com.freshdigitable.yttt.FetchSubscriptionListSourceFromYouTubeUseCase
import com.freshdigitable.yttt.FetchSubscriptionListSourceUseCase
import com.freshdigitable.yttt.data.model.TwitchId
import com.freshdigitable.yttt.data.model.YouTubeId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@Suppress("unused")
@Module
@InstallIn(ViewModelComponent::class)
interface FetchSubscriptionListUseCaseModule {
    @Binds
    @IntoMap
    @IdBaseClassKey(YouTubeId::class)
    fun bindFetchSubscriptionListFromYouTubeUseCase(
        useCase: FetchSubscriptionListSourceFromYouTubeUseCase,
    ): FetchSubscriptionListSourceUseCase

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchId::class)
    fun bindFetchSubscriptionListFromTwitchUseCase(
        useCase: FetchSubscriptionListSourceFromTwitchUseCase,
    ): FetchSubscriptionListSourceUseCase
}
