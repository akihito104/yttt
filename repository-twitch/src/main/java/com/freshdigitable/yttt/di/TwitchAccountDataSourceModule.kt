package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.TwitchAccountRemoteDataStoreImpl
import com.freshdigitable.yttt.data.TwitchAccountRepository
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.source.AccountRepository
import com.freshdigitable.yttt.data.source.TwitchAccountDataStore
import com.freshdigitable.yttt.data.source.TwitchOauthDataStore
import com.freshdigitable.yttt.data.source.TwitchOauthRemoteDataStore
import com.freshdigitable.yttt.data.source.local.TwitchAccountLocalDataSource
import com.freshdigitable.yttt.data.source.remote.TwitchOauthService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface TwitchAccountDataSourceModule {
    @Binds
    fun bindTwitchOauthDataStoreLocal(dataStore: TwitchAccountLocalDataSource): TwitchOauthDataStore.Local

    @Binds
    fun bindTwitchAccountDataStoreLocal(dataStore: TwitchAccountLocalDataSource): TwitchAccountDataStore.Local

    @Singleton
    @Binds
    @IntoMap
    @LivePlatformKey(Twitch::class)
    @LivePlatformQualifier(Twitch::class)
    fun bindAccountRepository(repository: TwitchAccountRepository): AccountRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal interface TwitchAccountModule {
    companion object {
        @Provides
        @Singleton
        fun provideTwitchOauthService(): TwitchOauthService {
            val retrofit = Retrofit.Builder()
                .baseUrl(TwitchOauthService.BASE_URL)
                .build()
            return retrofit.create(TwitchOauthService::class.java)
        }
    }

    @Binds
    @Singleton
    fun bindTwitchAccountRemoteDataSource(impl: TwitchAccountRemoteDataStoreImpl): TwitchOauthRemoteDataStore
}
