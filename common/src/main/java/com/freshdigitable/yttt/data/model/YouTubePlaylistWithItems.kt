package com.freshdigitable.yttt.data.model

import java.time.Duration
import java.time.Instant

interface YouTubePlaylistWithItems : YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id> {
    val items: Collection<YouTubePlaylistItem>
    val addedItems: Collection<YouTubePlaylistItem>
    override val itemId: List<YouTubePlaylistItem.Id>
        get() = items.map { it.id }

    companion object {
        fun YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>.update(
            newItems: Collection<YouTubePlaylistItem>?,
            fetchedAt: Instant,
        ): YouTubePlaylistWithItems = ForUpdate(
            newItems = newItems,
            cachedPlaylistWithItems = this,
            fetchedAt = fetchedAt,
        )

        fun newPlaylist(
            playlist: YouTubePlaylist,
            items: Collection<YouTubePlaylistItem>?,
            fetchedAt: Instant,
        ): YouTubePlaylistWithItems = NewPlaylist(
            playlist = playlist,
            newItems = items,
            fetchedAt = fetchedAt,
        )

        fun create(
            playlist: YouTubePlaylistUpdatable,
            items: Collection<YouTubePlaylistItem>
        ): YouTubePlaylistWithItems = CachedPlaylist(playlist, items)

        val MAX_AGE_DEFAULT: Duration = Duration.ofMinutes(10)
        internal val MAX_AGE_MAX: Duration = Duration.ofDays(1)
        private val MAX_AGE_FOR_ACTIVE_ACCOUNT: Duration = Duration.ofMinutes(30)
        internal val RECENTLY_BOARDER: Duration = Duration.ofDays(5)

        internal fun getMaxAgeUpperLimit(isPublishedRecently: Boolean): Duration =
            if (isPublishedRecently) MAX_AGE_FOR_ACTIVE_ACCOUNT else MAX_AGE_MAX
    }

    private class ForUpdate(
        private val cachedPlaylistWithItems: YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>,
        private val newItems: Collection<YouTubePlaylistItem>?,
        override val fetchedAt: Instant,
    ) : YouTubePlaylistWithItems {
        override val playlist: YouTubePlaylist
            get() = cachedPlaylistWithItems.playlist
        override val items: Collection<YouTubePlaylistItem>
            get() = newItems ?: emptyList()
        override val maxAge: Duration
            get() = if (items.isEmpty()) {
                MAX_AGE_MAX
            } else {
                val cachedIds = cachedPlaylistWithItems.itemId.toSet()
                val newIds = items.map { it.id }.toSet()
                val isNotModified = cachedIds == newIds
                if (isNotModified) {
                    val boarder = fetchedAt - RECENTLY_BOARDER
                    val isPublishedRecently = items.any { boarder < it.publishedAt }
                    val maxAgeMax = getMaxAgeUpperLimit(isPublishedRecently)
                    cachedPlaylistWithItems.maxAge.multipliedBy(2)
                        .coerceIn(MAX_AGE_DEFAULT..maxAgeMax)
                } else {
                    MAX_AGE_DEFAULT
                }
            }
        override val addedItems: Collection<YouTubePlaylistItem>
            get() {
                val i = items.associateBy { it.id }
                val addedId = i.keys - cachedPlaylistWithItems.itemId.toSet()
                return addedId.mapNotNull { i[it] }
            }
    }

    private class NewPlaylist(
        override val playlist: YouTubePlaylist,
        private val newItems: Collection<YouTubePlaylistItem>?,
        override val fetchedAt: Instant,
    ) : YouTubePlaylistWithItems {
        override val items: Collection<YouTubePlaylistItem>
            get() = newItems ?: emptyList()
        override val maxAge: Duration
            get() = if (items.isEmpty()) MAX_AGE_MAX else MAX_AGE_DEFAULT
        override val addedItems: Collection<YouTubePlaylistItem>
            get() = items
    }

    private class CachedPlaylist(
        override val playlist: YouTubePlaylistUpdatable,
        override val items: Collection<YouTubePlaylistItem>,
    ) : YouTubePlaylistWithItems, Updatable by playlist {
        override val addedItems: Collection<YouTubePlaylistItem>
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
