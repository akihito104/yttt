package com.freshdigitable.yttt.feature.oauth.di

import com.freshdigitable.yttt.data.model.YouTube
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.feature.oauth.AccountSettingListItem
import com.freshdigitable.yttt.feature.oauth.YouTubeAccountSettingListItem
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(ViewModelComponent::class)
internal interface YouTubeAccountSettingModules {
    @Binds
    @IntoMap
    @LivePlatformKey(YouTube::class)
    fun bindYouTubeListItem(item: YouTubeAccountSettingListItem): AccountSettingListItem

    @Module
    @InstallIn(ViewModelComponent::class)
    class Providers {
        @Provides
        fun provideYouTubeListItem(): YouTubeAccountSettingListItem = YouTubeAccountSettingListItem
    }
}
