package com.freshdigitable.yttt.di

import android.content.Context
import android.content.Intent
import com.freshdigitable.yttt.data.source.AccountLocalDataSource
import com.freshdigitable.yttt.data.source.remote.HttpRequestInitializerImpl
import com.freshdigitable.yttt.NewChooseAccountIntentProvider
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.youtube.YouTubeScopes
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object YouTubeModule {
    @Provides
    fun provideCredential(@ApplicationContext context: Context): GoogleAccountCredential {
        return GoogleAccountCredential.usingOAuth2(context, listOf(YouTubeScopes.YOUTUBE_READONLY))
            .setBackOff(ExponentialBackOff())
    }

    @Provides
    fun provideHttpRequestInitializer(
        credential: GoogleAccountCredential,
        dataStore: AccountLocalDataSource,
    ): HttpRequestInitializer = HttpRequestInitializerImpl(credential, dataStore)

    @Provides
    fun provideNewChooseAccountIntentProvider(
        credential: GoogleAccountCredential,
    ): NewChooseAccountIntentProvider = object : NewChooseAccountIntentProvider {
        override fun invoke(): Intent = credential.newChooseAccountIntent()
    }
}
