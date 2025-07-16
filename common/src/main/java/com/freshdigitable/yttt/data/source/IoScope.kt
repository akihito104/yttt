package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IoScope @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun <T> asResult(block: suspend CoroutineScope.() -> T): Result<T> =
        withContext(ioDispatcher) { runCatching { block(this) } }

    fun <T> asResultFlow(block: suspend FlowCollector<Result<T>>.() -> Unit): Flow<Result<T>> =
        flow {
            runCatching { block(this) }.onFailure { emit(Result.failure(it)) }
        }.flowOn(ioDispatcher)
}

interface NetworkResponse<T> : Updatable<T> {
    override val item: T
    val nextPageToken: String? get() = null
    override val cacheControl: CacheControl

    abstract class Exception(throwable: Throwable?) : kotlin.Exception(throwable) {
        abstract val statusCode: Int
        open val cacheControl: CacheControl get() = CacheControl.EMPTY
        open val isQuotaExceeded: Boolean get() = false
    }

    companion object {
        fun <T> create(
            item: T,
            cacheControl: CacheControl,
            nextPageToken: String? = null,
            eTag: String? = null,
        ): NetworkResponse<T> = Impl(item, cacheControl, nextPageToken, eTag)

        fun <T> create(
            updatable: Updatable<T>,
            nextPageToken: String? = null,
        ): NetworkResponse<T> = create(updatable.item, updatable.cacheControl, nextPageToken)

        fun <T, R> NetworkResponse<T>.map(mapper: (T) -> R): NetworkResponse<R> =
            Impl(mapper(this.item), this.cacheControl, this.nextPageToken)
    }

    private data class Impl<T>(
        override val item: T,
        override val cacheControl: CacheControl,
        override val nextPageToken: String?,
        override val eTag: String? = null,
    ) : NetworkResponse<T>
}
