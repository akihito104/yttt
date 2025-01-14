package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistTable
import java.time.Duration
import java.time.Instant

class YouTubePlaylistUpdatable(
    val playlistId: YouTubePlaylist.Id,
    private val playlist: YouTubePlaylist?,
    private val cachedItems: Collection<YouTubePlaylistItem> = emptyList(),
    private val newItems: Collection<YouTubePlaylistItem>?,
    val fetchedAt: Instant,
) {
    init {
        checkNotNull(playlist == null || playlistId == playlist.id)
    }

    val items: Collection<YouTubePlaylistItem>
        get() = newItems ?: emptyList()
    val maxAge: Duration
        get() = if (items.isEmpty()) {
            MAX_AGE_MAX
        } else if (playlist == null) {
            MAX_AGE_DEFAULT
        } else {
            val cachedIds = cachedItems.map { it.id }.toSet()
            val newIds = items.map { it.id }.toSet()
            val isNotModified = (cachedIds - newIds).isEmpty() && (newIds - cachedIds).isEmpty()
            if (isNotModified) {
                val boarder = fetchedAt - RECENTLY_BOARDER
                val isPublishedRecently = items.any { boarder < it.publishedAt }
                val maxAgeMax = getMaxAgeUpperLimit(isPublishedRecently)
                (playlist as YouTubePlaylistTable).maxAge.multipliedBy(2)
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
        ): YouTubePlaylistUpdatable = YouTubePlaylistUpdatable(
            playlistId = playlistId,
            playlist = null,
            newItems = items,
            fetchedAt = fetchedAt,
        )

        internal val MAX_AGE_DEFAULT: Duration = Duration.ofMinutes(10)
        internal val MAX_AGE_MAX: Duration = Duration.ofDays(1)
        private val MAX_AGE_FOR_ACTIVE_ACCOUNT: Duration = Duration.ofMinutes(30)
        internal val RECENTLY_BOARDER: Duration = Duration.ofDays(5)

        internal fun getMaxAgeUpperLimit(isPublishedRecently: Boolean): Duration =
            if (isPublishedRecently) MAX_AGE_FOR_ACTIVE_ACCOUNT else MAX_AGE_MAX
    }
}
