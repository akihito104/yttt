package com.freshdigitable.yttt.feature.oauth.di

import com.freshdigitable.yttt.feature.oauth.AccountSettingListItem
import com.freshdigitable.yttt.feature.oauth.YouTubeAccountSettingListItem
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(ViewModelComponent::class)
internal interface YouTubeAccountSettingModules {
    companion object {
        @Provides
        @IntoSet
        fun provideYouTubeListItem(): AccountSettingListItem = YouTubeAccountSettingListItem
    }
}
