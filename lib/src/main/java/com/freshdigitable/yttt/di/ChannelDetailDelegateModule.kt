package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.ChannelDetailDelegate
import com.freshdigitable.yttt.ChannelDetailDelegateForTwitch
import com.freshdigitable.yttt.ChannelDetailDelegateForYouTube
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.YouTubeChannel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@Suppress("unused")
@Module
@InstallIn(ViewModelComponent::class)
interface ChannelDetailDelegateModule {
    @Binds
    @IntoMap
    @IdBaseClassKey(YouTubeChannel.Id::class)
    fun bindChannelDetailDelegateFactoryYouTube(
        factory: ChannelDetailDelegateForYouTube.Factory,
    ): ChannelDetailDelegate.Factory

    @Binds
    @IntoMap
    @IdBaseClassKey(TwitchUser.Id::class)
    fun bindChannelDetailDelegateFactoryTwitch(
        factory: ChannelDetailDelegateForTwitch.Factory,
    ): ChannelDetailDelegate.Factory
}
