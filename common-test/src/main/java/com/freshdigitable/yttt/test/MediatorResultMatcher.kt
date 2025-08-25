package com.freshdigitable.yttt.test

import androidx.paging.ExperimentalPagingApi
import androidx.paging.RemoteMediator
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
