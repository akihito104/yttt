package com.freshdigitable.yttt.feature.subscription.di

import com.freshdigitable.yttt.data.model.TwitchId
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.feature.subscription.FetchSubscriptionListSourceFromTwitchUseCase
import com.freshdigitable.yttt.feature.subscription.FetchSubscriptionListSourceUseCase
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
    @IdBaseClassKey(TwitchId::class)
    fun bindFetchSubscriptionListFromTwitchUseCase(
        useCase: FetchSubscriptionListSourceFromTwitchUseCase,
    ): FetchSubscriptionListSourceUseCase
}
