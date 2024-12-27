package com.freshdigitable.yttt.di

import com.freshdigitable.yttt.compose.ImageLoaderViewSetup
import com.freshdigitable.yttt.compose.image.glide.setup
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ImageLoaderGlideViewProvider {
    companion object {
        @Provides
        @Singleton
        fun provideSetup(): ImageLoaderViewSetup = setup
    }
}
