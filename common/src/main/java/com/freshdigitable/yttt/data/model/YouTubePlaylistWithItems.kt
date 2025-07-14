package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.CacheControl.Companion.overrideMaxAge
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import java.time.Duration
import kotlin.math.pow

interface YouTubePlaylistWithItems<T : YouTubePlaylistItemIds> {
    val playlist: YouTubePlaylist
    val items: List<T>
    val addedItems: List<T> get() = emptyList()
    val eTag: String? get() = null

    companion object {
        private val YouTubePlaylistWithItems<*>.itemId: List<YouTubePlaylistItem.Id> get() = items.map { it.id }
        fun YouTubePlaylistWithItems<*>.update(
            newItems: Updatable<List<YouTubePlaylistItem>>,
        ): Updatable<YouTubePlaylistWithItemDetails> = ForUpdate(
            newItems = newItems,
            cachedPlaylistWithItems = this,
        ).toUpdatable(ForUpdate.CacheControlImpl(newItems, this))

        fun newPlaylist(
            playlist: Updatable<YouTubePlaylist>,
            items: Updatable<List<YouTubePlaylistItem>?>,
        ): Updatable<YouTubePlaylistWithItemDetails> = NewPlaylist(playlist.item, items)
            .toUpdatable(
                Updatable.latest(playlist, items).cacheControl
                    .overrideMaxAge(if (items.item.isNullOrEmpty()) MAX_AGE_MAX else MAX_AGE_DEFAULT)
            )

        internal val MAX_AGE_MAX: Duration = Duration.ofDays(1)
        val MAX_AGE_DEFAULT: Duration = MAX_AGE_MAX.dividedBy(2.0.pow(n = 7).toLong())
    }

    private class ForUpdate(
        private val cachedPlaylistWithItems: YouTubePlaylistWithItems<*>,
        private val newItems: Updatable<List<YouTubePlaylistItem>>,
    ) : YouTubePlaylistWithItemDetails {
        override val playlist: YouTubePlaylist
            get() = cachedPlaylistWithItems.playlist
        override val items: List<YouTubePlaylistItem>
            get() = newItems.item
        override val addedItems: List<YouTubePlaylistItem>
            get() {
                val i = items.associateBy { it.id }
                val addedId = i.keys - cachedPlaylistWithItems.itemId.toSet()
                return addedId.mapNotNull { i[it] }
            }
        override val eTag: String? get() = newItems.eTag

        class CacheControlImpl(
            private val items: Updatable<List<YouTubePlaylistItem>>,
            private val cachedPlaylistWithItems: YouTubePlaylistWithItems<*>,
        ) : CacheControl by items.cacheControl {
            override val maxAge: Duration
                get() = if (items.item.isEmpty()) {
                    MAX_AGE_MAX
                } else {
                    val cachedIds = cachedPlaylistWithItems.itemId.toSet()
                    val newIds = items.item.map { it.id }.toSet()
                    val isNotModified = cachedIds == newIds
                    if (isNotModified) {
                        val latest = items.item.maxOf { it.publishedAt }
                        val inactionDays =
                            Duration.between(latest, fetchedAt).toDays().coerceIn(0L..7)
                        val pow = 2.0.pow(inactionDays.toDouble())
                        MAX_AGE_DEFAULT.multipliedBy(pow.toLong())
                    } else {
                        MAX_AGE_DEFAULT
                    }
                }
        }
    }

    private class NewPlaylist(
        override val playlist: YouTubePlaylist,
        private val newItems: Updatable<List<YouTubePlaylistItem>?>,
    ) : YouTubePlaylistWithItemDetails {
        override val items: List<YouTubePlaylistItem> get() = newItems.item ?: emptyList()
        override val addedItems: List<YouTubePlaylistItem> get() = items
        override val eTag: String? get() = newItems.eTag
    }
}

typealias YouTubePlaylistWithItemIds = YouTubePlaylistWithItems<YouTubePlaylistItemIds>
typealias YouTubePlaylistWithItemDetails = YouTubePlaylistWithItems<YouTubePlaylistItem>

internal fun Updatable.Companion.latest(u1: Updatable<*>, u2: Updatable<*>): Updatable<*> {
    check(u1.cacheControl.fetchedAt != null || u2.cacheControl.fetchedAt != null)
    val f1 = u1.cacheControl.fetchedAt ?: return u2
    val f2 = u2.cacheControl.fetchedAt ?: return u1
    return if (f1 < f2) u2 else u1
}
