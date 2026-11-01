package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.di.LivePlatformKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

internal class TwitchAnnotatableStringFactory : AnnotatableString.Factory {
    override fun accountUrlCandidate(account: String): List<String> =
        listOf("https://twitch.tv/${account.substring(1)}")
}

@Module
@InstallIn(SingletonComponent::class)
interface TwitchAnnotatableStringFactoryModule {
    companion object {
        @Provides
        @IntoMap
        @LivePlatformKey(Twitch::class)
        fun provideAnnotatableStringFactory(): AnnotatableString.Factory =
            TwitchAnnotatableStringFactory()
    }
}
