package com.freshdigitable.yttt.data.source

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.RemoteMediator

interface PagerFactory<Q, T : Any> {
    fun create(query: Q, config: PagingConfig): Pager<Int, T>
}

interface RemoteMediatorFactory<T : Any> {
    @OptIn(ExperimentalPagingApi::class)
    fun create(): RemoteMediator<Int, T>
}

interface RemoteMediatorFactoryWithQuery<Q, T : Any> {
    @OptIn(ExperimentalPagingApi::class)
    fun create(query: Q): RemoteMediator<Int, T>
}

interface PagingSourceFunction<T : Any> {
    fun create(): PagingSource<Int, T>
}

interface PagingSourceFunctionWithQuery<Q, T : Any> {
    fun create(query: Q): PagingSource<Int, T>
}
