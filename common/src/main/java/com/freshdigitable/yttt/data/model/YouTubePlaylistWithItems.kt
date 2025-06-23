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
        ): YouTubePlaylistWithItems = NewPlaylist(
            playlist = playlist,
            newItems = items,
        )

        fun create(
            playlist: YouTubePlaylist,
            items: List<YouTubePlaylistItem>,
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
    ) : YouTubePlaylistWithItems {
        override val items: List<YouTubePlaylistItem>
            get() = newItems ?: emptyList()
        override val fetchedAt: Instant
            get() = items.firstOrNull()?.let { checkNotNull(it.fetchedAt) }
                ?: checkNotNull(playlist.cacheControl.fetchedAt)
        override val maxAge: Duration
            get() = if (items.isEmpty()) MAX_AGE_MAX else MAX_AGE_DEFAULT
        override val addedItems: List<YouTubePlaylistItem>
            get() = items
    }

    private class CachedPlaylist(
        override val playlist: YouTubePlaylist,
        override val items: List<YouTubePlaylistItem>,
    ) : YouTubePlaylistWithItems, Updatable by playlist {
        override val addedItems: List<YouTubePlaylistItem>
            get() = emptyList()
        override val fetchedAt: Instant? get() = cacheControl.fetchedAt
        override val maxAge: Duration? get() = cacheControl.maxAge
    }
}

interface YouTubePlaylistWithItemSummaries : YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id> {
    val summary: Collection<YouTubePlaylistItemSummary>
    override val itemId: List<YouTubePlaylistItem.Id>
        get() = summary.map { it.playlistItemId }
}

interface YouTubePlaylistWithItemIds<T : IdBase> : CacheControl {
    val playlist: YouTubePlaylist
    val itemId: List<T>
}
