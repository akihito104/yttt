package com.freshdigitable.yttt.di

import android.content.Context
import android.content.Intent
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.freshdigitable.yttt.data.source.YouTubeAccountDataStore
import com.freshdigitable.yttt.data.source.YoutubeDataSource
import com.freshdigitable.yttt.data.source.remote.HttpRequestInitializerImpl
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object YouTubeModule {
    @Provides
    @Singleton
    fun provideCredential(@ApplicationContext context: Context): GoogleAccountCredential {
        return GoogleAccountCredential.usingOAuth2(context, listOf(YouTubeScopes.YOUTUBE_READONLY))
            .setBackOff(ExponentialBackOff())
    }

    @Provides
    @Singleton
    fun provideHttpRequestInitializer(
        credential: GoogleAccountCredential,
        dataStore: YouTubeAccountDataStore.Local,
    ): HttpRequestInitializer = HttpRequestInitializerImpl(credential, dataStore)

    @Provides
    @Singleton
    fun provideYouTubeClient(httpRequestInitializer: HttpRequestInitializer): YouTube =
        YouTube.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            httpRequestInitializer,
        ).build()

    @Provides
    @Singleton
    fun provideNewChooseAccountIntentProvider(
        credential: GoogleAccountCredential,
    ): NewChooseAccountIntentProvider = object : NewChooseAccountIntentProvider {
        override fun invoke(): Intent = credential.newChooseAccountIntent()
    }

    @Module
    @InstallIn(SingletonComponent::class)
    internal interface Bind {
        @Binds
        fun bindYoutubeDataSourceRemote(dataSource: YouTubeRemoteDataSource): YoutubeDataSource.Remote
    }
}
