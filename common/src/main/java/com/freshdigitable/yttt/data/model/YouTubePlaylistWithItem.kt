package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.CacheControl.Companion.overrideMaxAge
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import java.time.Duration
import kotlin.math.pow

interface YouTubePlaylistWithItem<T : YouTubePlaylistItem> {
    val playlist: YouTubePlaylist
    val items: List<T>
    val addedItems: List<T> get() = emptyList()
    val eTag: String? get() = null

    companion object {
        private const val DAYS_OF_WEEK = 7
        private val YouTubePlaylistWithItem<*>.itemId: List<YouTubePlaylistItem.Id> get() = items.map { it.id }
        fun <T : YouTubePlaylistItem> YouTubePlaylistWithItem<*>.update(
            newItems: Updatable<List<T>>,
        ): Updatable<YouTubePlaylistWithItem<T>> = ForUpdate(
            cachedPlaylistWithItems = this,
            newItems = newItems,
        ).toUpdatable(ForUpdate.CacheControlImpl(newItems.item, newItems.cacheControl, this))

        fun <T : YouTubePlaylistItem> newPlaylist(
            playlist: Updatable<YouTubePlaylist>,
            items: Updatable<List<T>>,
        ): Updatable<YouTubePlaylistWithItem<T>> = NewPlaylist(playlist.item, items)
            .toUpdatable(
                Updatable.latest(playlist, items).cacheControl
                    .overrideMaxAge(if (items.item.isEmpty()) MAX_AGE_MAX else MAX_AGE_DEFAULT),
            )

        internal val MAX_AGE_MAX: Duration = Duration.ofDays(1)
        val MAX_AGE_DEFAULT: Duration = MAX_AGE_MAX.dividedBy(2.0.pow(n = DAYS_OF_WEEK).toLong())
    }

    private class ForUpdate<T : YouTubePlaylistItem>(
        private val cachedPlaylistWithItems: YouTubePlaylistWithItem<*>,
        private val newItems: Updatable<List<T>>,
    ) : YouTubePlaylistWithItem<T> {
        override val playlist: YouTubePlaylist
            get() = cachedPlaylistWithItems.playlist
        override val items: List<T>
            get() = newItems.item
        override val addedItems: List<T>
            get() {
                val i = items.associateBy { it.id }
                val addedId = i.keys - cachedPlaylistWithItems.itemId.toSet()
                return addedId.mapNotNull { i[it] }
            }
        override val eTag: String? get() = newItems.eTag

        class CacheControlImpl(
            private val items: List<YouTubePlaylistItem>,
            cacheControl: CacheControl,
            private val cachedPlaylistWithItems: YouTubePlaylistWithItem<*>,
        ) : CacheControl by cacheControl {
            override val maxAge: Duration
                get() = if (items.isEmpty()) {
                    MAX_AGE_MAX
                } else {
                    val cachedIds = cachedPlaylistWithItems.itemId.toSet()
                    val newIds = items.map { it.id }.toSet()
                    val isNotModified = cachedIds == newIds
                    if (isNotModified) {
                        val latest = items.maxOf { it.publishedAt }
                        val inactionDays =
                            Duration.between(latest, fetchedAt).toDays().coerceIn(0L..DAYS_OF_WEEK)
                        val pow = 2.0.pow(inactionDays.toDouble())
                        MAX_AGE_DEFAULT.multipliedBy(pow.toLong())
                    } else {
                        MAX_AGE_DEFAULT
                    }
                }
        }
    }

    private class NewPlaylist<T : YouTubePlaylistItem>(
        override val playlist: YouTubePlaylist,
        private val newItems: Updatable<List<T>>,
    ) : YouTubePlaylistWithItem<T> {
        override val items: List<T> get() = newItems.item
        override val addedItems: List<T> get() = items
        override val eTag: String? get() = newItems.eTag
    }
}

typealias YouTubePlaylistWithItems = YouTubePlaylistWithItem<YouTubePlaylistItem>
typealias YouTubePlaylistWithItemDetails = YouTubePlaylistWithItem<YouTubePlaylistItemDetail>

internal fun Updatable.Companion.latest(u1: Updatable<*>, u2: Updatable<*>): Updatable<*> {
    val f1 = u1.cacheControl.fetchedAt
    val f2 = u2.cacheControl.fetchedAt
    check(f1 != null || f2 != null)
    return when {
        f1 == null -> u2
        f2 == null -> u1
        else -> if (f1 < f2) u2 else u1
    }
}
