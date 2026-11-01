package com.freshdigitable.yttt.feature.channel.di

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.di.IdBaseClassKey
import com.freshdigitable.yttt.feature.channel.ChannelDetailDelegate
import com.freshdigitable.yttt.feature.channel.ChannelDetailDelegateForYouTube
import com.freshdigitable.yttt.feature.channel.ChannelDetailPageComposableFactory
import com.freshdigitable.yttt.feature.channel.YouTubeChannelDetailPageComposableFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
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

@Module
@InstallIn(ActivityComponent::class)
internal interface YouTubeChannelDetailModule {
    companion object {
        @IdBaseClassKey(YouTubeChannel.Id::class)
        @IntoMap
        @Provides
        fun provideChannelDetailPageComposeFactory(): ChannelDetailPageComposableFactory =
            YouTubeChannelDetailPageComposableFactory
    }
}
