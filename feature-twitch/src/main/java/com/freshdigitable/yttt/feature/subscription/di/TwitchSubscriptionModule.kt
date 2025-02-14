package com.freshdigitable.yttt.feature.subscription.di

import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.feature.subscription.WatchSubscriptionPagingDataFromTwitchUseCase
import com.freshdigitable.yttt.feature.subscription.WatchSubscriptionPagingDataUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@Suppress("unused")
@Module
@InstallIn(ViewModelComponent::class)
internal interface TwitchSubscriptionModule {
    @Binds
    @IntoMap
    @LivePlatformKey(Twitch::class)
    fun bindWatchSubscriptionPagingDataFromTwitchUseCase(
        useCase: WatchSubscriptionPagingDataFromTwitchUseCase,
    ): WatchSubscriptionPagingDataUseCase
}
