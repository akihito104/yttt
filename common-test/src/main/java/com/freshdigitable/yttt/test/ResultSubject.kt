package com.freshdigitable.yttt.test

import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.ThrowableSubject
import com.google.common.truth.Truth

class ResultSubject<T>(
    metadata: FailureMetadata,
    private val actual: Result<T>?,
) : Subject(metadata, actual) {

    companion object {
        fun <T> factory(): Factory<ResultSubject<T>, Result<T>> =
            Factory { metadata, actual -> ResultSubject(metadata, actual) }

        fun <T> assertResultThat(actual: Result<T>): ResultSubject<T> =
            Truth.assertAbout(factory<T>()).that(actual)
    }

    fun isSuccess() {
        check("isSuccess").that(actual?.isSuccess).isTrue()
    }

    fun isFailure() {
        check("isFailure").that(actual?.isFailure).isTrue()
    }

    fun value(): Subject = check("value").that(actual?.getOrNull())
    fun throwable(): ThrowableSubject = check("throwable").that(actual?.exceptionOrNull())
}
