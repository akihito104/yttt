package com.freshdigitable.yttt.data.model

import java.time.Duration
import java.time.Instant

class YouTubePlaylistItemsUpdatable(
    val playlistId: YouTubePlaylist.Id,
    private val cachedPlaylistWithItems: YouTubePlaylistWithItems?,
    private val newItems: Collection<YouTubePlaylistItem>?,
    override val fetchedAt: Instant,
) : Updatable {
    private val playlist: YouTubePlaylist? get() = cachedPlaylistWithItems?.playlist
    private val cachedItems: Collection<YouTubePlaylistItem>
        get() = cachedPlaylistWithItems?.items ?: emptyList()

    init {
        checkNotNull(playlist == null || playlistId == playlist?.id)
    }

    val items: Collection<YouTubePlaylistItem>
        get() = newItems ?: emptyList()
    override val maxAge: Duration
        get() = if (items.isEmpty()) {
            MAX_AGE_MAX
        } else if (playlist == null) {
            MAX_AGE_DEFAULT
        } else {
            val cachedIds = cachedItems.map { it.id }.toSet()
            val newIds = items.map { it.id }.toSet()
            val isNotModified = cachedIds == newIds
            if (isNotModified) {
                val boarder = fetchedAt - RECENTLY_BOARDER
                val isPublishedRecently = items.any { boarder < it.publishedAt }
                val maxAgeMax = getMaxAgeUpperLimit(isPublishedRecently)
                requireNotNull(cachedPlaylistWithItems).maxAge.multipliedBy(2)
                    .coerceIn(MAX_AGE_DEFAULT..maxAgeMax)
            } else {
                MAX_AGE_DEFAULT
            }
        }

    companion object {
        fun nullOrEmpty(
            playlistId: YouTubePlaylist.Id,
            items: Collection<YouTubePlaylistItem>?,
            fetchedAt: Instant
        ): YouTubePlaylistItemsUpdatable = YouTubePlaylistItemsUpdatable(
            playlistId = playlistId,
            cachedPlaylistWithItems = null,
            newItems = items,
            fetchedAt = fetchedAt,
        )

        val MAX_AGE_DEFAULT: Duration = Duration.ofMinutes(10)
        internal val MAX_AGE_MAX: Duration = Duration.ofDays(1)
        private val MAX_AGE_FOR_ACTIVE_ACCOUNT: Duration = Duration.ofMinutes(30)
        internal val RECENTLY_BOARDER: Duration = Duration.ofDays(5)

        internal fun getMaxAgeUpperLimit(isPublishedRecently: Boolean): Duration =
            if (isPublishedRecently) MAX_AGE_FOR_ACTIVE_ACCOUNT else MAX_AGE_MAX
    }
}
