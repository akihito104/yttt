package com.freshdigitable.yttt.di

import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.data.YouTubeSubscriptionPagerFactory
import com.freshdigitable.yttt.data.model.DateTimeProvider
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.source.IoScope
import com.freshdigitable.yttt.data.source.PagerFactory
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.data.source.YouTubeDataSource
import com.freshdigitable.yttt.data.source.remote.HttpRequestInitializerImpl
import com.freshdigitable.yttt.data.source.remote.YouTubeClient
import com.freshdigitable.yttt.data.source.remote.YouTubeRemoteDataSource
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.YouTubeScopes
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface YouTubeModule {
    companion object {
        @Provides
        @Singleton
        fun provideCredential(@ApplicationContext context: Context): GoogleAccountCredential =
            GoogleAccountCredential.usingOAuth2(context, listOf(YouTubeScopes.YOUTUBE_READONLY))
                .setBackOff(ExponentialBackOff())

        @Provides
        @Singleton
        fun provideHttpRequestInitializer(
            credential: GoogleAccountCredential,
            dataStore: YouTubeAccountDataStore.Local,
        ): HttpRequestInitializer = HttpRequestInitializerImpl(credential, dataStore)

        @Provides
        @Singleton
        fun provideYouTube(httpRequestInitializer: HttpRequestInitializer): YouTube =
            YouTube.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                httpRequestInitializer,
            ).build()

        @Provides
        @Singleton
        fun provideYouTubeClient(
            youTube: YouTube,
            dateTimeProvider: DateTimeProvider,
        ): YouTubeClient = YouTubeClient.create(youTube, dateTimeProvider)

        @Provides
        @Singleton
        fun provideNewChooseAccountIntentProvider(
            credential: GoogleAccountCredential,
        ): NewChooseAccountIntentProvider = object : NewChooseAccountIntentProvider {
            override fun invoke(): Intent = credential.newChooseAccountIntent()
        }
    }
}

@VisibleForTesting
@Module
@InstallIn(SingletonComponent::class)
interface YouTubeRemoteDataSourceModule {
    companion object {
        @Provides
        @Singleton
        fun provideRemoteDataSource(
            client: YouTubeClient,
            ioScope: IoScope,
            dateTimeProvides: DateTimeProvider,
        ): YouTubeDataSource.Remote = YouTubeRemoteDataSource(client, ioScope, dateTimeProvides)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface PagerFactoryModule {
    @Binds
    @IntoMap
    @LivePlatformKey(com.freshdigitable.yttt.data.model.YouTube::class)
    fun bindPagerFactory(factory: YouTubeSubscriptionPagerFactory): PagerFactory<LiveSubscription>
}
