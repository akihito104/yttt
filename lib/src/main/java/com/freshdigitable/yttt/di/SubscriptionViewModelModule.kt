package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.PagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@InstallIn(ViewModelComponent::class)
@Module
internal interface SubscriptionViewModelModule {
    companion object {
        @Provides
        fun provideAccountRepositoryMap(
            platforms: ClassMap<LivePlatform, LivePlatform>,
            accountRepositories: ClassMap<LivePlatform, AccountRepository>,
        ): LivePlatformMap<AccountRepository> = platforms.toMap(accountRepositories)

        @Provides
        fun providePagerFactory(
            platforms: ClassMap<LivePlatform, LivePlatform>,
            pagerFactories: ClassMap<LivePlatform, PagerFactory<LiveSubscription>>,
        ): LivePlatformMap<PagerFactory<LiveSubscription>> = platforms.toMap(pagerFactories)
    }
}
