package com.freshdigitable.yttt.data.model

import java.time.Duration
import java.time.Instant

interface YouTubePlaylistWithItems : Updatable {
    val playlist: YouTubePlaylist
    val items: Collection<YouTubePlaylistItem>

    companion object {
        fun YouTubePlaylistWithItems.update(
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
        private val cachedPlaylistWithItems: YouTubePlaylistWithItems,
        private val newItems: Collection<YouTubePlaylistItem>?,
        override val fetchedAt: Instant,
    ) : YouTubePlaylistWithItems {
        override val playlist: YouTubePlaylist get() = cachedPlaylistWithItems.playlist
        private val cachedItems: Collection<YouTubePlaylistItem>
            get() = cachedPlaylistWithItems.items
        override val items: Collection<YouTubePlaylistItem>
            get() = newItems ?: emptyList()
        override val maxAge: Duration
            get() = if (items.isEmpty()) {
                MAX_AGE_MAX
            } else {
                val cachedIds = cachedItems.map { it.id }.toSet()
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
    }

    private class CachedPlaylist(
        override val playlist: YouTubePlaylistUpdatable,
        override val items: Collection<YouTubePlaylistItem>,
    ) : YouTubePlaylistWithItems, Updatable by playlist
}
