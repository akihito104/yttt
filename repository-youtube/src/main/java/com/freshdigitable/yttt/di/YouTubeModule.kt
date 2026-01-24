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
import com.freshdigitable.yttt.data.source.remote.YouTubeClientWrapper
import com.freshdigitable.yttt.data.source.remote.YouTubeRemoteDataSource
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface YouTubeModule {
    companion object {
        var rootUrl: String? = null

        @Provides
        @Singleton
        fun provideYouTube(okhttpClient: OkHttpClient, httpRequestInitializer: HttpRequestInitializer): YouTube =
            YouTube.Builder(
                HttpTransportOkHttp(okhttpClient.newBuilder().build()),
                GsonFactory.getDefaultInstance(),
                httpRequestInitializer,
            ).apply {
                this@Companion.rootUrl?.let { setRootUrl(it) }
            }.build()

        @Provides
        @Singleton
        fun provideYouTubeClient(
            youTube: YouTube,
            dateTimeProvider: DateTimeProvider,
        ): YouTubeClient = YouTubeClient.create(youTube)
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface GoogleAccountModule {
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
        ): YouTubeDataSource.Remote = YouTubeRemoteDataSource(YouTubeClientWrapper.create(client, ioScope))
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

internal class HttpTransportOkHttp(
    private val okHttp: OkHttpClient,
) : HttpTransport() {
    override fun buildRequest(
        method: String,
        url: String,
    ): LowLevelHttpRequest {
        val okHttp = this.okHttp
        return object : LowLevelHttpRequest() {
            private val requestBuilder = Request.Builder()
                .url(url)
                .method(method, null)

            override fun addHeader(name: String, value: String) {
                requestBuilder.header(name, value)
            }

            override fun execute(): LowLevelHttpResponse {
                val request = requestBuilder.build()
                val response = okHttp.newCall(request).execute()
                return object : LowLevelHttpResponse() {
                    override fun getContent(): InputStream = response.body.byteStream()
                    override fun getContentEncoding(): String? = response.header("Content-Encoding")
                    override fun getContentLength(): Long = response.body.contentLength()
                    override fun getContentType(): String? = response.body.contentType()?.toString()
                    override fun getStatusLine(): String? {
                        val line = response.headers.value(0)
                        return if (line.startsWith("HTTP/1.")) {
                            line
                        } else {
                            null
                        }
                    }

                    override fun getStatusCode(): Int = response.code
                    override fun getReasonPhrase(): String = response.message
                    override fun getHeaderCount(): Int = response.headers.size
                    override fun getHeaderName(index: Int): String = response.headers.name(index)
                    override fun getHeaderValue(index: Int): String = response.headers.value(index)
                    override fun disconnect() {
                        response.close()
                    }
                }
            }
        }
    }
}
