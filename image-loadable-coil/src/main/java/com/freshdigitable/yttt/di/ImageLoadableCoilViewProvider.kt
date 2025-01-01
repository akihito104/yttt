package com.freshdigitable.yttt.di

import android.app.Application
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import com.freshdigitable.yttt.compose.ImageLoadableView
import com.freshdigitable.yttt.compose.ImageLoaderViewSetup
import com.freshdigitable.yttt.compose.image.coil.ImageLoadableCoilView
import com.freshdigitable.yttt.data.source.ImageDataSource
import com.freshdigitable.yttt.logD
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ImageLoadableCoilViewProvider {
    companion object {
        @Provides
        @Singleton
        fun provideDiskCache(context: Application): DiskCache = DiskCache.Builder()
            .directory(File(context.cacheDir, "coil_cache").toOkioPath())
            .maxSizePercent(0.02)
            .build()

        @Provides
        @Singleton
        fun provideMemoryCache(context: Application): MemoryCache = MemoryCache.Builder()
            .maxSizePercent(context, 0.25)
            .build()

        @Provides
        @Singleton
        fun provideSetup(
            okHttpClient: OkHttpClient,
            diskCache: DiskCache,
            memoryCache: MemoryCache,
            dataStore: ImageDataStoreImpl,
        ): ImageLoaderViewSetup = {
            SingletonImageLoader.setSafe { context ->
                ImageLoader.Builder(context)
                    .memoryCache { memoryCache }
                    .diskCache { diskCache }
                    .eventListener(object : EventListener() {
                        override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                            val diskCacheKey = result.diskCacheKey ?: return
                            val memoryCacheKey = result.memoryCacheKey ?: return
                            dataStore.putMemoryCacheKey(diskCacheKey, memoryCacheKey)
                        }
                    })
                    .components {
                        add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                    }
                    .crossfade(true)
                    .build()
            }
        }

        @Provides
        @Singleton
        fun provideImageLoadableViewDelegate(): ImageLoadableView.Delegate = ImageLoadableCoilView
    }

    @Binds
    fun bindImageDataStore(dataStore: ImageDataStoreImpl): ImageDataSource
}

@Singleton
internal class ImageDataStoreImpl @Inject constructor(
    private val diskCache: DiskCache,
    private val memoryCache: MemoryCache,
) : ImageDataSource {
    private val memoryCacheKey = mutableMapOf<String, MemoryCache.Key>()
    fun putMemoryCacheKey(key: String, value: MemoryCache.Key) {
        memoryCacheKey[key] = value
    }

    override fun removeImageByUrl(url: Collection<String>) {
        url.forEach {
            diskCache.remove(it)
            val k = memoryCacheKey.remove(it) ?: return@forEach
            memoryCache.remove(k)
        }
    }
}
