package com.freshdigitable.yttt.feature.channel.di

import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.feature.channel.ChannelDetailDelegate
import com.freshdigitable.yttt.feature.channel.ChannelDetailDelegateForTwitch
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
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
