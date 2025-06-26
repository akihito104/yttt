package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.CacheControl
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
            runCatching { block(this) }.onFailure { emit(Result.failure<T>(it)) }
        }.flowOn(ioDispatcher)
}

interface NetworkResponse<T> {
    val item: T
    val nextPageToken: String? get() = null

    abstract class Exception(throwable: Throwable?) : kotlin.Exception(throwable) {
        abstract val statusCode: Int
        open val cacheControl: CacheControl get() = CacheControl.empty()
        open val isQuotaExceeded: Boolean get() = false
    }

    companion object {
        fun <T> create(item: T, nextPageToken: String? = null): NetworkResponse<T> =
            Impl(item, nextPageToken)
    }

    private data class Impl<T>(override val item: T, override val nextPageToken: String?) :
        NetworkResponse<T>
}
