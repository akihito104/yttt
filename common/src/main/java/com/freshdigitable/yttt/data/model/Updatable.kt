package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.CacheControl.Companion.checkUpdatableBy
import com.freshdigitable.yttt.data.model.CacheControl.Companion.isUpdatable
import com.freshdigitable.yttt.data.model.CacheControl.Companion.overrideMaxAge
import java.time.Duration
import java.time.Instant

interface Updatable<T> {
    val item: T
    val cacheControl: CacheControl
    val eTag: String? get() = null

    companion object {
        fun <T> create(item: T, cacheControl: CacheControl): Updatable<T> = Impl(item, cacheControl)
        fun <T> T.toUpdatable(
            fetchedAt: Instant? = Instant.EPOCH,
            maxAge: Duration? = Duration.ZERO,
        ): Updatable<T> = this.toUpdatable(CacheControl.create(fetchedAt, maxAge))

        fun <T> T.toUpdatable(cacheControl: CacheControl): Updatable<T> = create(this, cacheControl)

        fun Updatable<*>.isUpdatable(current: Instant): Boolean = cacheControl.isUpdatable(current)
        fun Updatable<*>.isFresh(current: Instant): Boolean = !isUpdatable(current)
        fun Updatable<*>.checkUpdatableBy(new: Updatable<*>) =
            this.cacheControl.checkUpdatableBy(new.cacheControl)

        fun <T> Updatable<T>.overrideMaxAge(maxAge: Duration): Updatable<T> =
            Impl(this.item, this.cacheControl.overrideMaxAge(maxAge))

        fun <T, R> Updatable<T>.map(mapper: (T) -> R): Updatable<R> =
            Impl(mapper(this.item), this.cacheControl)

        fun <T> Updatable<List<T>>.flattenToList(): List<Updatable<T>> =
            item.map { it.toUpdatable(cacheControl) }
    }

    private class Impl<T>(override val item: T, override val cacheControl: CacheControl) :
        Updatable<T>
}

interface CacheControl {
    val fetchedAt: Instant?
    val maxAge: Duration?

    companion object {
        val EMPTY: CacheControl = Impl(null, null)
        fun create(fetchedAt: Instant?, maxAge: Duration?): CacheControl = Impl(fetchedAt, maxAge)

        fun CacheControl.isUpdatable(current: Instant): Boolean {
            val f = fetchedAt ?: return true
            val a = maxAge ?: return true
            return f + a <= current
        }

        fun CacheControl.isFresh(current: Instant): Boolean = !isUpdatable(current)

        internal fun CacheControl.checkUpdatableBy(new: CacheControl) {
            val f = this.fetchedAt ?: return
            require(f < checkNotNull(new.fetchedAt)) {
                "old.fetchedAt: ${this.fetchedAt}, new.fetchedAt: ${new.fetchedAt}"
            }
        }

        fun CacheControl.overrideMaxAge(maxAge: Duration): CacheControl =
            Impl(this.fetchedAt, maxAge)
    }

    private class Impl(override val fetchedAt: Instant?, override val maxAge: Duration?) :
        CacheControl
}
