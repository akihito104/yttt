package com.freshdigitable.yttt.feature.oauth.di

import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.di.LivePlatformKey
import com.freshdigitable.yttt.feature.oauth.AccountSettingListItem
import com.freshdigitable.yttt.feature.oauth.TwitchAccountSettingListItem
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(ViewModelComponent::class)
internal interface TwitchAccountSettingModules {
    @Binds
    @IntoMap
    @LivePlatformKey(Twitch::class)
    fun bindTwitchListItem(item: TwitchAccountSettingListItem): AccountSettingListItem

    @Module
    @InstallIn(ViewModelComponent::class)
    class Providers {
        @Provides
        fun provideTwitchListItem(): TwitchAccountSettingListItem = TwitchAccountSettingListItem
    }
}
