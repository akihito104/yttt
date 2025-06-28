package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.CacheControl.Companion.overrideMaxAge
import java.time.Duration
import java.time.Instant
import kotlin.math.pow

interface YouTubePlaylistWithItems : YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id> {
    val items: List<YouTubePlaylistItem>
    val addedItems: List<YouTubePlaylistItem>
    override val itemId: List<YouTubePlaylistItem.Id>
        get() = items.map { it.id }

    companion object {
        fun YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>.update(
            newItems: List<YouTubePlaylistItem>?,
            fetchedAt: Instant,
        ): Updatable<YouTubePlaylistWithItems> = Updatable.create(
            item = ForUpdate(
                newItems = newItems,
                cachedPlaylistWithItems = this,
            ),
            cacheControl = ForUpdate.CacheControlImpl(fetchedAt, newItems ?: emptyList(), this),
        )

        fun newPlaylist(
            playlist: Updatable<YouTubePlaylist>,
            items: Updatable<List<YouTubePlaylistItem>?>,
        ): Updatable<YouTubePlaylistWithItems> = Updatable.create(
            item = NewPlaylist(playlist.item, items.item),
            cacheControl = Updatable.latest(playlist, items).cacheControl
                .overrideMaxAge(if (items.item.isNullOrEmpty()) MAX_AGE_MAX else MAX_AGE_DEFAULT),
        )

        fun create(
            playlist: Updatable<YouTubePlaylist>,
            items: Updatable<List<YouTubePlaylistItem>>,
        ): Updatable<YouTubePlaylistWithItems> = Updatable.create(
            item = CachedPlaylist(playlist.item, items.item),
            cacheControl = Updatable.latest(playlist, items).cacheControl,
        )

        internal val MAX_AGE_MAX: Duration = Duration.ofDays(1)
        val MAX_AGE_DEFAULT: Duration = MAX_AGE_MAX.dividedBy(2.0.pow(n = 7).toLong())
    }

    private class ForUpdate(
        private val cachedPlaylistWithItems: YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>,
        private val newItems: List<YouTubePlaylistItem>?,
    ) : YouTubePlaylistWithItems {
        override val playlist: YouTubePlaylist
            get() = cachedPlaylistWithItems.playlist
        override val items: List<YouTubePlaylistItem>
            get() = newItems ?: emptyList()
        override val addedItems: List<YouTubePlaylistItem>
            get() {
                val i = items.associateBy { it.id }
                val addedId = i.keys - cachedPlaylistWithItems.itemId.toSet()
                return addedId.mapNotNull { i[it] }
            }

        class CacheControlImpl(
            override val fetchedAt: Instant,
            private val items: List<YouTubePlaylistItem>,
            private val cachedPlaylistWithItems: YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>,
        ) : CacheControl {
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
        private val newItems: List<YouTubePlaylistItem>?,
    ) : YouTubePlaylistWithItems {
        override val items: List<YouTubePlaylistItem>
            get() = newItems ?: emptyList()
        override val addedItems: List<YouTubePlaylistItem>
            get() = items
    }

    private class CachedPlaylist(
        override val playlist: YouTubePlaylist,
        override val items: List<YouTubePlaylistItem>,
    ) : YouTubePlaylistWithItems {
        override val addedItems: List<YouTubePlaylistItem>
            get() = emptyList()
    }
}

interface YouTubePlaylistWithItemSummaries : YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id> {
    val summary: Collection<YouTubePlaylistItemSummary>
    override val itemId: List<YouTubePlaylistItem.Id>
        get() = summary.map { it.playlistItemId }
}

interface YouTubePlaylistWithItemIds<T : IdBase> {
    val playlist: YouTubePlaylist
    val itemId: List<T>
}

internal fun Updatable.Companion.latest(u1: Updatable<*>, u2: Updatable<*>): Updatable<*> {
    check(u1.cacheControl.fetchedAt != null || u2.cacheControl.fetchedAt != null)
    val f1 = u1.cacheControl.fetchedAt ?: return u2
    val f2 = u2.cacheControl.fetchedAt ?: return u1
    return if (f1 < f2) u2 else u1
}
