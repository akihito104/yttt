package com.freshdigitable.yttt.di

import android.app.Application
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.logD
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object OkHttpModule {
    @Provides
    @Singleton
    fun provideCache(context: Application): Cache = Cache(
        directory = File(context.cacheDir, "http_cache"),
        maxSize = 100L * 1024L * 1024L,
    )

    @Provides
    @Singleton
    fun provideImageDataStore(cache: Cache): ImageDataSource = ImageDataStoreImpl(cache)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        interceptors: Set<@JvmSuppressWildcards Interceptor>,
        cache: Cache,
    ): OkHttpClient = OkHttpClient.Builder().apply {
        interceptors.forEach { addInterceptor(it) }
    }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .cache(cache)
        .build()
}

private class ImageDataStoreImpl(
    private val cache: Cache,
) : ImageDataSource {
    override fun removeImageByUrl(url: Collection<String>) {
        if (url.isEmpty()) {
            return
        }
        val urls = cache.urls()
        val keys = url.toMutableSet()
        for (u in urls) {
            if (keys.remove(u)) {
                urls.remove()
                logD { "provideImageDataStore: removed>$u" }
            }
            if (keys.isEmpty()) {
                logD { "provideImageDataStore: completed" }
                return
            }
        }
        logD { "provideImageDataStore: end>$keys" }
    }
}
