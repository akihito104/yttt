package com.freshdigitable.yttt.feature.subscription.di

import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.feature.subscription.FetchSubscriptionListSourceFromYouTubeUseCase
import com.freshdigitable.yttt.feature.subscription.FetchSubscriptionListSourceUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(ViewModelComponent::class)
internal interface YouTubeSubscriptionModule {
    @Binds
    @IntoMap
    @LivePlatformKey(YouTube::class)
    fun bindFetchSubscriptionListFromYouTubeUseCase(
        useCase: FetchSubscriptionListSourceFromYouTubeUseCase,
    ): FetchSubscriptionListSourceUseCase
}
