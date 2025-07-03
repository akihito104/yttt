package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.CacheControl.Companion.overrideMaxAge
import com.freshdigitable.yttt.data.model.Updatable.Companion.toUpdatable
import java.time.Duration
import kotlin.math.pow

interface YouTubePlaylistWithItems : YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id> {
    val items: List<YouTubePlaylistItem>
    val addedItems: List<YouTubePlaylistItem>
    override val itemId: List<YouTubePlaylistItem.Id>
        get() = items.map { it.id }

    companion object {
        fun YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>.update(
            newItems: Updatable<List<YouTubePlaylistItem>>,
        ): Updatable<YouTubePlaylistWithItems> = ForUpdate(
            newItems = newItems.item,
            cachedPlaylistWithItems = this,
        ).toUpdatable(ForUpdate.CacheControlImpl(newItems, this))

        fun newPlaylist(
            playlist: Updatable<YouTubePlaylist>,
            items: Updatable<List<YouTubePlaylistItem>?>,
        ): Updatable<YouTubePlaylistWithItems> = NewPlaylist(playlist.item, items.item)
            .toUpdatable(
                Updatable.latest(playlist, items).cacheControl
                    .overrideMaxAge(if (items.item.isNullOrEmpty()) MAX_AGE_MAX else MAX_AGE_DEFAULT)
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
            private val items: Updatable<List<YouTubePlaylistItem>>,
            private val cachedPlaylistWithItems: YouTubePlaylistWithItemIds<YouTubePlaylistItem.Id>,
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
        private val newItems: List<YouTubePlaylistItem>?,
    ) : YouTubePlaylistWithItems {
        override val items: List<YouTubePlaylistItem>
            get() = newItems ?: emptyList()
        override val addedItems: List<YouTubePlaylistItem>
            get() = items
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
