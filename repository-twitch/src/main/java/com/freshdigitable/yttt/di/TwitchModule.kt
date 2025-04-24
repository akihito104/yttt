package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.TwitchAccountRemoteDataStoreImpl
import com.freshdigitable.yttt.data.TwitchSubscriptionPagerFactory
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.source.PagerFactory
import com.freshdigitable.yttt.data.source.TwitchAccountRemoteDataStore
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.remote.IdConverterFactory
import com.freshdigitable.yttt.data.source.remote.TwitchHelixService
import com.freshdigitable.yttt.data.source.remote.TwitchOauthService
import com.freshdigitable.yttt.data.source.remote.TwitchRemoteDataSource
import com.freshdigitable.yttt.data.source.remote.TwitchTokenInterceptor
import com.freshdigitable.yttt.data.source.remote.createGson
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface TwitchModule {
    companion object {
        @Provides
        @Singleton
        fun provideGson(): Gson = createGson()

        @Provides
        @Singleton
        fun provideTwitchApiRetrofit(
            gson: Gson,
            okhttp: OkHttpClient,
            interceptor: TwitchTokenInterceptor,
        ): Retrofit {
            val client = okhttp.newBuilder()
                .addInterceptor(interceptor)
                .build()
            return Retrofit.Builder()
                .baseUrl(TwitchHelixService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addConverterFactory(IdConverterFactory())
                .client(client)
                .build()
        }

        @Provides
        @Singleton
        fun provideTwitchHelixService(retrofit: Retrofit): TwitchHelixService =
            retrofit.create(TwitchHelixService::class.java)
    }
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
    fun bindTwitchAccountRemoteDataSource(impl: TwitchAccountRemoteDataStoreImpl): TwitchAccountRemoteDataStore
}

@Module
@InstallIn(SingletonComponent::class)
internal interface TwitchDataSourceModule {
    @Binds
    fun bindTwitchDataSourceRemote(dataSource: TwitchRemoteDataSource): TwitchDataSource.Remote

    @Singleton
    @Binds
    @IntoMap
    @LivePlatformKey(Twitch::class)
    fun bindRemoteMediatorFactory(factory: TwitchSubscriptionPagerFactory): PagerFactory<LiveSubscription>
}
