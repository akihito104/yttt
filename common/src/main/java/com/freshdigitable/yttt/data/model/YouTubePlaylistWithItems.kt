package com.freshdigitable.yttt.data.model

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
        ): YouTubePlaylistWithItems = ForUpdate(
            newItems = newItems,
            cachedPlaylistWithItems = this,
            fetchedAt = fetchedAt,
        )

        fun newPlaylist(
            playlist: YouTubePlaylist,
            items: List<YouTubePlaylistItem>?,
            fetchedAt: Instant,
        ): YouTubePlaylistWithItems = NewPlaylist(
            playlist = playlist,
            newItems = items,
            fetchedAt = fetchedAt,
        )

        fun create(
            playlist: YouTubePlaylistUpdatable,
            items: List<YouTubePlaylistItem>
        ): YouTubePlaylistWithItems = CachedPlaylist(playlist, items)

        internal val MAX_AGE_MAX: Duration = Duration.ofDays(1)
        val MAX_AGE_DEFAULT: Duration = MAX_AGE_MAX.dividedBy(2.0.pow(n = 7).toLong())
    }

    private class ForUpdate(
        private val cachedPlaylistWithItems: YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>,
        private val newItems: List<YouTubePlaylistItem>?,
        override val fetchedAt: Instant,
    ) : YouTubePlaylistWithItems {
        override val playlist: YouTubePlaylist
            get() = cachedPlaylistWithItems.playlist
        override val items: List<YouTubePlaylistItem>
            get() = newItems ?: emptyList()
        override val maxAge: Duration
            get() = if (items.isEmpty()) {
                MAX_AGE_MAX
            } else {
                val cachedIds = cachedPlaylistWithItems.itemId.toSet()
                val newIds = items.map { it.id }.toSet()
                val isNotModified = cachedIds == newIds
                if (isNotModified) {
                    val latest = items.maxOf { it.publishedAt }
                    val inactionDays = Duration.between(latest, fetchedAt).toDays().coerceIn(0L..7)
                    val pow = 2.0.pow(inactionDays.toDouble())
                    MAX_AGE_DEFAULT.multipliedBy(pow.toLong())
                } else {
                    MAX_AGE_DEFAULT
                }
            }
        override val addedItems: List<YouTubePlaylistItem>
            get() {
                val i = items.associateBy { it.id }
                val addedId = i.keys - cachedPlaylistWithItems.itemId.toSet()
                return addedId.mapNotNull { i[it] }
            }
    }

    private class NewPlaylist(
        override val playlist: YouTubePlaylist,
        private val newItems: List<YouTubePlaylistItem>?,
        override val fetchedAt: Instant,
    ) : YouTubePlaylistWithItems {
        override val items: List<YouTubePlaylistItem>
            get() = newItems ?: emptyList()
        override val maxAge: Duration
            get() = if (items.isEmpty()) MAX_AGE_MAX else MAX_AGE_DEFAULT
        override val addedItems: List<YouTubePlaylistItem>
            get() = items
    }

    private class CachedPlaylist(
        override val playlist: YouTubePlaylistUpdatable,
        override val items: List<YouTubePlaylistItem>,
    ) : YouTubePlaylistWithItems, Updatable by playlist {
        override val addedItems: List<YouTubePlaylistItem>
            get() = emptyList()
    }
}

interface YouTubePlaylistWithItemSummaries : YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id> {
    val summary: Collection<YouTubePlaylistItemSummary>
    override val itemId: List<YouTubePlaylistItem.Id>
        get() = summary.map { it.playlistItemId }
}

interface YouTubePlaylistWithItemIds<T : IdBase> : Updatable {
    val playlist: YouTubePlaylist
    val itemId: List<T>
}
