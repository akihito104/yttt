package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.source.remote.TwitchHelixService
import com.freshdigitable.yttt.data.source.remote.TwitchOauth
import com.freshdigitable.yttt.data.source.remote.TwitchTokenInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant

@Module
@InstallIn(SingletonComponent::class)
object TwitchModule {
    @Provides
    fun provideGson(): Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, object : TypeAdapter<Instant?>() {
            override fun write(out: JsonWriter?, value: Instant?) {
                out?.value(value?.toString())
            }

            override fun read(`in`: JsonReader?): Instant? {
                val str = `in`?.nextString() ?: return null
                return Instant.parse(str)
            }
        })
        .create()

    @Provides
    fun provideOkHttpClient(tokenInterceptor: TwitchTokenInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(tokenInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    fun provideTwitchApiRetrofit(gson: Gson, okhttp: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.twitch.tv/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okhttp)
            .build()
    }

    @Provides
    fun provideTwitchHelixService(retrofit: Retrofit): TwitchHelixService =
        retrofit.create(TwitchHelixService::class.java)

    @Provides
    fun provideTwitchOauthService(): TwitchOauth {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://id.twitch.tv/")
            .build()
        return retrofit.create(TwitchOauth::class.java)
    }
}
