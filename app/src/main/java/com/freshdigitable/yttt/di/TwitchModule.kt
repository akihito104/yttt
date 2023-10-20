package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.source.TwitchChannelSchedule
import com.freshdigitable.yttt.data.source.TwitchStream
import com.freshdigitable.yttt.data.source.TwitchUser
import com.freshdigitable.yttt.data.source.TwitchVideo
import com.freshdigitable.yttt.data.source.remote.TwitchHelixService
import com.freshdigitable.yttt.data.source.remote.TwitchOauth
import com.freshdigitable.yttt.data.source.remote.TwitchTokenInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
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
import java.lang.reflect.Type
import java.time.Instant

@Module
@InstallIn(SingletonComponent::class)
object TwitchModule {
    @Provides
    fun provideGson(): Gson = GsonBuilder()
        .registerTypeAdapter<Instant, String>(
            deserialize = { it?.let { s -> Instant.parse(s) } },
            serialize = { it?.toString() },
        )
        .registerTypeAdapter<TwitchUser.Id, String>(
            deserialize = { it?.let { s -> TwitchUser.Id(s) } },
            serialize = { it?.value },
        )
        .registerJsonDeserializer<TwitchStream.Id, String> { it?.let { s -> TwitchStream.Id(s) } }
        .registerJsonDeserializer<TwitchVideo.Id, String> { it?.let { s -> TwitchVideo.Id(s) } }
        .registerTypeAdapter<TwitchChannelSchedule.Stream.Id, String>(
            deserialize = { it?.let { s -> TwitchChannelSchedule.Stream.Id(s) } },
            serialize = { it?.value },
        )
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

    private inline fun <reified O, reified S> GsonBuilder.registerJsonDeserializer(
        crossinline deserialize: (S?) -> O?,
    ): GsonBuilder = registerTypeAdapter(O::class.java, object : JsonDeserializer<O?> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type,
            context: JsonDeserializationContext,
        ): O? {
            val v = when (S::class) {
                String::class -> json?.asString
                Boolean::class -> json?.asBoolean
                Int::class -> json?.asInt
                Long::class -> json?.asLong
                Double::class -> json?.asDouble
                else -> throw IllegalStateException()
            }
            return deserialize(v as S?)
        }
    })

    private inline fun <reified O, reified S> GsonBuilder.registerJsonSerializer(
        crossinline serialize: (O?) -> S?,
    ): GsonBuilder = registerTypeAdapter(O::class.java, object : JsonSerializer<O?> {
        override fun serialize(
            src: O?,
            typeOfSrc: Type,
            context: JsonSerializationContext,
        ): JsonElement = context.serialize(serialize(src), S::class.java)
    })

    private inline fun <reified O, reified S> GsonBuilder.registerTypeAdapter(
        crossinline deserialize: (S?) -> O?,
        crossinline serialize: (O?) -> S?,
    ): GsonBuilder = registerTypeAdapter(O::class.java, object : TypeAdapter<O?>() {
        override fun write(out: JsonWriter?, value: O?) {
            when (val v = serialize(value)) {
                is String -> out?.value(v)
                is Boolean -> out?.value(v)
                is Long -> out?.value(v)
                is Double -> out?.value(v)
                is Number -> out?.value(v)
                else -> throw AssertionError("unsupported type: $v")
            }
        }

        override fun read(`in`: JsonReader?): O? {
            val v: S? = when (S::class) {
                String::class -> `in`?.nextString() as S?
                Boolean::class -> `in`?.nextBoolean() as S?
                Int::class -> `in`?.nextInt() as S?
                Long::class -> `in`?.nextLong() as S?
                Double::class -> `in`?.nextDouble() as S?
                else -> throw AssertionError("unsupported type: ${S::class}")
            }
            return deserialize(v)
        }
    })
}
