package com.freshdigitable.yttt.data.source

import com.freshdigitable.yttt.data.model.CacheControl
import com.freshdigitable.yttt.data.model.Updatable
import com.freshdigitable.yttt.data.source.NetworkResponse.Exception.Companion.HTTP_STATUS_NOT_FOUND
import com.freshdigitable.yttt.data.source.NetworkResponse.Exception.Companion.HTTP_STATUS_NOT_MODIFIED
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IoScope @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun <T> asResult(block: suspend CoroutineScope.() -> T): Result<T> =
        withContext(ioDispatcher) { runCatching { block(this) } }
}

inline fun <R, T : R> Result<T>.recoverFromNotModified(block: (CacheControl) -> R): Result<R> =
    recoverFromNetworkErrorResponse(HTTP_STATUS_NOT_MODIFIED, block)

inline fun <R, T : R> Result<T>.recoverFromNotFoundError(block: (CacheControl) -> R): Result<R> =
    recoverFromNetworkErrorResponse(HTTP_STATUS_NOT_FOUND, block)

inline fun <R, T : R> Result<T>.recoverFromNetworkErrorResponse(
    statusCode: Int,
    block: (CacheControl) -> R,
): Result<R> = recoverCatching {
    if ((it as? NetworkResponse.Exception)?.statusCode == statusCode) {
        block(it.cacheControl)
    } else {
        throw it
    }
}

interface NetworkResponse<T> : Updatable<T> {
    override val item: T
    val nextPageToken: String? get() = null
    override val cacheControl: CacheControl

    abstract class Exception(throwable: Throwable?) : kotlin.Exception(throwable) {
        companion object {
            const val HTTP_STATUS_NOT_MODIFIED = 304
            const val HTTP_STATUS_NOT_FOUND = 404
            val HTTP_STATUS_USER_ERROR_RANGE = 400..499
            val HTTP_STATUS_INTERNAL_ERROR_RANGE = 500..599
        }

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
            eTag: String? = null,
        ): NetworkResponse<T> = create(updatable.item, updatable.cacheControl, nextPageToken, eTag)

        fun <T, R> NetworkResponse<T>.map(mapper: (T) -> R): NetworkResponse<R> =
            Impl(mapper(this.item), this.cacheControl, this.nextPageToken, this.eTag)
    }

    private data class Impl<T>(
        override val item: T,
        override val cacheControl: CacheControl,
        override val nextPageToken: String?,
        override val eTag: String? = null,
    ) : NetworkResponse<T>
}
