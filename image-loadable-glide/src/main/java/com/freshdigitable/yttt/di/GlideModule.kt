package com.freshdigitable.yttt.di

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader.Factory
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.InputStream

@GlideModule
@Excludes(OkHttpLibraryGlideModule::class)
class GlideModule : AppGlideModule() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppGlideEntryPoint {
        val defaultOkHttpClient: OkHttpClient
        val cache: Cache
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val entryPoint =
            EntryPointAccessors.fromApplication(context, AppGlideEntryPoint::class.java)
        val okHttpClient = entryPoint.defaultOkHttpClient.newBuilder()
            .cache(cache = entryPoint.cache)
            .build()
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            Factory(okHttpClient),
        )
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setDefaultRequestOptions(RequestOptions().diskCacheStrategy(DiskCacheStrategy.NONE))
    }
}
