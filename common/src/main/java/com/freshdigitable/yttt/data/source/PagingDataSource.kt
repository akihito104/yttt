package com.freshdigitable.yttt.data.source

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.RemoteMediator
import com.freshdigitable.yttt.data.model.LivePlatform
import com.freshdigitable.yttt.di.ClassMap

@OptIn(ExperimentalPagingApi::class)
interface PagerFactory<T : Any> {
    fun create(config: PagingConfig): Pager<Int, T>

    companion object {
        fun <T : Any> newInstance(
            remoteMediator: RemoteMediator<Int, T>,
            pagingSourceFunction: PagingSourceFunction<T>,
        ): PagerFactory<T> = Impl(remoteMediator, pagingSourceFunction)
    }

    private class Impl<T : Any>(
        private val remoteMediator: RemoteMediator<Int, T>,
        private val pagingSourceFunction: PagingSourceFunction<T>,
    ) : PagerFactory<T> {
        @OptIn(ExperimentalPagingApi::class)
        override fun create(config: PagingConfig): Pager<Int, T> = Pager(
            config = config,
            remoteMediator = remoteMediator,
            pagingSourceFactory = pagingSourceFunction::invoke,
        )
    }
}

interface PagingSourceFunction<T : Any> {
    operator fun invoke(): PagingSource<Int, T>
}

fun <T : Any> ClassMap<LivePlatform, PagerFactory<T>>.create(
    platform: LivePlatform,
    config: PagingConfig,
): Pager<Int, T> = checkNotNull(this[platform::class.java]).create(config)
