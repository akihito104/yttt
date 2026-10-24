package com.freshdigitable.yttt.feature.channel.di

import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.feature.channel.ChannelDetailDelegate
import com.freshdigitable.yttt.feature.channel.ChannelDetailDelegateForTwitch
import com.freshdigitable.yttt.feature.channel.ChannelDetailPageComposableFactory
import com.freshdigitable.yttt.feature.channel.TwitchChannelDetailPageComposableFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(ViewModelComponent::class)
internal interface TwitchChannelModule {
    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchUser.Id::class)
    fun bindChannelDetailDelegateFactoryTwitch(
        factory: ChannelDetailDelegateForTwitch.Factory,
    ): ChannelDetailDelegate.Factory
}

@Module
@InstallIn(ActivityComponent::class)
internal interface TwitchChannelDetailModule {
    companion object {
        @IdBaseClassKey(TwitchUser.Id::class)
        @IntoMap
        @Provides
        fun provideChannelDetailPageComposableFactory(): ChannelDetailPageComposableFactory =
            TwitchChannelDetailPageComposableFactory
    }
}
