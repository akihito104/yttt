package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.data.TwitchSubscriptionPagerFactory
import com.freshdigitable.yttt.data.model.LiveSubscription
import com.freshdigitable.yttt.data.model.Twitch
import com.freshdigitable.yttt.data.model.TwitchCategory
import com.freshdigitable.yttt.data.model.TwitchChannelSchedule
import com.freshdigitable.yttt.data.model.TwitchId
import com.freshdigitable.yttt.data.model.TwitchStream
import com.freshdigitable.yttt.data.model.TwitchUser
import com.freshdigitable.yttt.data.model.TwitchVideo
import com.freshdigitable.yttt.data.source.PagerFactory
import com.freshdigitable.yttt.data.source.TwitchDataSource
import com.freshdigitable.yttt.data.source.remote.TwitchHelixService
import com.freshdigitable.yttt.data.source.remote.TwitchOauthService
import com.freshdigitable.yttt.data.source.remote.TwitchRemoteDataSource
import com.freshdigitable.yttt.data.source.remote.TwitchTokenInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.time.Instant
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface TwitchModule {
    companion object {
        @Provides
        @Singleton
        fun provideGson(): Gson = GsonBuilder()
            .registerTypeAdapter<Instant, String>(
                deserialize = { it?.let { s -> Instant.parse(s) } },
                serialize = { it?.toString() },
            )
            .registerIdJsonDeserializer { it?.let { s -> TwitchUser.Id(s) } }
            .registerIdJsonDeserializer { it?.let { s -> TwitchStream.Id(s) } }
            .registerIdJsonDeserializer { it?.let { s -> TwitchVideo.Id(s) } }
            .registerIdJsonDeserializer { it?.let { s -> TwitchChannelSchedule.Stream.Id(s) } }
            .registerIdJsonDeserializer { it?.let { s -> TwitchCategory.Id(s) } }
            .create()

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
                .baseUrl("https://api.twitch.tv/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addConverterFactory(object : Converter.Factory() {
                    override fun stringConverter(
                        type: Type,
                        annotations: Array<out Annotation>,
                        retrofit: Retrofit
                    ): Converter<*, String>? {
                        if (type == TwitchCategory.Id::class.java) {
                            return Converter<TwitchId, String> { it.value }
                        }
                        return null
                    }
                })
                .client(client)
                .build()
        }

        @Provides
        @Singleton
        fun provideTwitchHelixService(retrofit: Retrofit): TwitchHelixService =
            retrofit.create(TwitchHelixService::class.java)

        @Provides
        @Singleton
        fun provideTwitchOauthService(): TwitchOauthService {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://id.twitch.tv/")
                .build()
            return retrofit.create(TwitchOauthService::class.java)
        }

        private inline fun <reified O : TwitchId> GsonBuilder.registerIdJsonDeserializer(
            crossinline deserialize: (String?) -> O?,
        ): GsonBuilder = registerJsonDeserializer<O, String>(deserialize)

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
                val peek = `in`?.peek()
                if (peek == JsonToken.NULL) {
                    `in`.nextNull()
                    return null
                }
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

    @Binds
    fun bindTwitchDataSourceRemote(dataSource: TwitchRemoteDataSource): TwitchDataSource.Remote

    @Singleton
    @Binds
    @IntoMap
    @LivePlatformKey(Twitch::class)
    fun bindRemoteMediatorFactory(factory: TwitchSubscriptionPagerFactory): PagerFactory<LiveSubscription>
}
