package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.compose.ImageLoaderViewSetup
import com.freshdigitable.yttt.compose.image.coil.setup
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ImageLoadableCoilViewProvider {
    companion object {
        @Provides
        @Singleton
        fun provideSetup(okHttpClient: OkHttpClient, cache: Cache): ImageLoaderViewSetup =
            setup(okHttpClient, cache)
    }
}
