package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.YouTube
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
interface PlatformModule {
    @Binds
    @IntoMap
    @LivePlatformKey(YouTube::class)
    fun bindYouTube(platform: YouTube): LivePlatform

    companion object {
        @Provides
        fun provideYouTube(): YouTube = YouTube
    }
}
