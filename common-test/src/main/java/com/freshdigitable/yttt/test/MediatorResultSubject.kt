package com.freshdigitable.yttt.test

import androidx.paging.ExperimentalPagingApi
import androidx.paging.RemoteMediator
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.ThrowableSubject
import com.google.common.truth.Truth

@OptIn(ExperimentalPagingApi::class)
class MediatorResultSubject(
    metadata: FailureMetadata,
    private val actual: RemoteMediator.MediatorResult?,
) : Subject(metadata, actual) {
    companion object {
        private fun factory(): Factory<MediatorResultSubject, RemoteMediator.MediatorResult> =
            Factory { metadata, actual -> MediatorResultSubject(metadata, actual) }

        fun assertThat(actual: RemoteMediator.MediatorResult?): MediatorResultSubject =
            Truth.assertAbout(factory()).that(actual)
    }

    fun isSuccess(endOfPaginationReached: Boolean) {
        check("MediatorResult.Success").that(actual)
            .isInstanceOf(RemoteMediator.MediatorResult.Success::class.java)
        check("endOfPaginationReached").that((actual as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
            .isEqualTo(endOfPaginationReached)
    }

    fun isError(throwableMatcher: (ThrowableSubject) -> Unit) {
        check("MediatorResult.Error").that(actual)
            .isInstanceOf(RemoteMediator.MediatorResult.Error::class.java)
        throwableMatcher(throwable())
    }

    private fun throwable(): ThrowableSubject =
        check("throwable").that((actual as RemoteMediator.MediatorResult.Error).throwable)
}
