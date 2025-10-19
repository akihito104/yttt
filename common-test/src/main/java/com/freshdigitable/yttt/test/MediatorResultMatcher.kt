package com.freshdigitable.yttt.test

import androidx.annotation.VisibleForTesting
import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.RemoteMediator
import androidx.paging.testing.TestPager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

@OptIn(ExperimentalPagingApi::class)
fun RemoteMediator.MediatorResult.shouldBeSuccess(endOfPaginationReached: Boolean) {
    this.shouldBeInstanceOf<RemoteMediator.MediatorResult.Success> {
        it.endOfPaginationReached shouldBe endOfPaginationReached
    }
}

@OptIn(ExperimentalPagingApi::class)
inline fun <reified T : Throwable> RemoteMediator.MediatorResult.shouldBeError() {
    this.shouldBeInstanceOf<RemoteMediator.MediatorResult.Error> {
        it.throwable.shouldBeInstanceOf<T>()
    }
}

@VisibleForTesting
suspend fun <T : Any> PagingSource<Int, T>.testWithRefresh(
    pageSize: Int = 5,
    verify: PagingSource.LoadResult.Page<Int, T>.() -> Unit,
) {
    val testPager = toTestPager(pageSize)
    val res = testPager.refresh() as PagingSource.LoadResult.Page
    verify(res)
}

@VisibleForTesting
fun <T : Any> PagingSource<Int, T>.toTestPager(pageSize: Int = 5): TestPager<Int, T> =
    TestPager(PagingConfig(pageSize), this)
