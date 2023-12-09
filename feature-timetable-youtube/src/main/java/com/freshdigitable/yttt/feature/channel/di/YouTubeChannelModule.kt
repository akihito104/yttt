package com.freshdigitable.yttt.feature.channel.di

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.feature.channel.ChannelDetailDelegate
import com.freshdigitable.yttt.feature.channel.ChannelDetailDelegateForYouTube
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(ViewModelComponent::class)
internal interface YouTubeChannelModule {
    @Binds
    @IntoMap
    @IdBaseClassKey(YouTubeChannel.Id::class)
    fun bindChannelDetailDelegateFactoryYouTube(
        factory: ChannelDetailDelegateForYouTube.Factory,
    ): ChannelDetailDelegate.Factory
}
