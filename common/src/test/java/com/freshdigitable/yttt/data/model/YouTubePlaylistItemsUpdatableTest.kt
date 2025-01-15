package com.freshdigitable.yttt.data.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(Enclosed::class)
class YouTubePlaylistItemsUpdatableTest {
    class Init {
        private val playlistId = YouTubePlaylist.Id("playlist")

        @Test
        fun nullOrEmpty_newItemIsNull_returnsMaxDuration() {
            // setup
            val sut = YouTubePlaylistItemsUpdatable.nullOrEmpty(
                playlistId,
                null,
                Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(YouTubePlaylistItemsUpdatable.MAX_AGE_MAX)
        }

        @Test
        fun nullOrEmpty_newItemIsEmpty_returnsMaxDuration() {
            // setup
            val sut = YouTubePlaylistItemsUpdatable.nullOrEmpty(
                playlistId,
                emptyList(),
                Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(YouTubePlaylistItemsUpdatable.MAX_AGE_MAX)
        }

        @Test
        fun init_playlistIsNotCached_returnsDefaultDuration() {
            // setup
            val sut = YouTubePlaylistItemsUpdatable(
                playlistId = playlistId,
                cachedPlaylistWithItems = null,
                newItems = listOf(
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_01"),
                    ),
                ),
                fetchedAt = Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(YouTubePlaylistItemsUpdatable.MAX_AGE_DEFAULT)
        }

        @Test
        fun init_playlistItemsIsModified_returnsDefaultDuration() {
            // setup
            val sut = YouTubePlaylistItemsUpdatable(
                playlistId = playlistId,
                cachedPlaylistWithItems = playlistWithItems(
                    playlistId = playlistId,
                    newItems = listOf(
                        playlistItem(
                            playlistId = playlistId,
                            itemId = YouTubePlaylistItem.Id("item_id_01")
                        ),
                    ),
                ),
                newItems = listOf(
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_02"),
                    ),
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_01")
                    ),
                ),
                fetchedAt = Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(YouTubePlaylistItemsUpdatable.MAX_AGE_DEFAULT)
        }
    }

    class PlaylistIsNotModified {
        private val playlistId = YouTubePlaylist.Id("playlist")
        private val newItems = listOf(
            playlistItem(
                playlistId = playlistId,
                itemId = YouTubePlaylistItem.Id("item_id_02"),
                publishedAt = Instant.ofEpochMilli(20000),
            ),
            playlistItem(
                playlistId = playlistId,
                itemId = YouTubePlaylistItem.Id("item_id_01"),
                publishedAt = Instant.ofEpochMilli(15000),
            ),
        )

        private fun playlistWithItems(
            maxAge: Duration,
            newItems: List<YouTubePlaylistItem>,
        ): YouTubePlaylistWithItems = playlistWithItems(playlistId, maxAge, newItems)

        @Test
        fun playlistOfActiveAccount_returnsTwiceOfMaxAge() {
            // setup
            val maxAge = Duration.ofMinutes(10)
            val sut = YouTubePlaylistItemsUpdatable(
                playlistId = playlistId,
                cachedPlaylistWithItems = playlistWithItems(maxAge, newItems),
                newItems = newItems,
                fetchedAt = Instant.ofEpochMilli(20000 - 1) + YouTubePlaylistItemsUpdatable.RECENTLY_BOARDER,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(maxAge.multipliedBy(2))
        }

        @Test
        fun playlistOfActiveAccount_returnsUpperLimitDuration() {
            // setup
            val maxAge = Duration.ofMinutes(20)
            val sut = YouTubePlaylistItemsUpdatable(
                playlistId = playlistId,
                cachedPlaylistWithItems = playlistWithItems(maxAge, newItems),
                newItems = newItems,
                fetchedAt = Instant.ofEpochMilli(20000 - 1) + YouTubePlaylistItemsUpdatable.RECENTLY_BOARDER,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(Duration.ofMinutes(30))
        }

        @Test
        fun playlistOfNotActiveAccount_returnsTwiceOfCurrentMaxAge() {
            // setup
            val maxAge = Duration.ofMinutes(20)
            val sut = YouTubePlaylistItemsUpdatable(
                playlistId = playlistId,
                cachedPlaylistWithItems = playlistWithItems(maxAge, newItems),
                newItems = newItems,
                fetchedAt = Instant.ofEpochMilli(20001) + YouTubePlaylistItemsUpdatable.RECENTLY_BOARDER,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(Duration.ofMinutes(40))
        }

        @Test
        fun playlistOfNotActiveAccount_returnsUpperLimitDuration() {
            // setup
            val maxAge = Duration.ofHours(15)
            val sut = YouTubePlaylistItemsUpdatable(
                playlistId = playlistId,
                cachedPlaylistWithItems = playlistWithItems(maxAge, newItems),
                newItems = newItems,
                fetchedAt = Instant.ofEpochMilli(20001) + YouTubePlaylistItemsUpdatable.RECENTLY_BOARDER,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(Duration.ofDays(1))
        }
    }
}

private fun playlist(playlistId: YouTubePlaylist.Id): YouTubePlaylist = object : YouTubePlaylist {
    override val id: YouTubePlaylist.Id = playlistId
    override val title: String = ""
    override val thumbnailUrl: String = ""
}

private fun playlistWithItems(
    playlistId: YouTubePlaylist.Id,
    maxAge: Duration = Duration.ZERO,
    newItems: List<YouTubePlaylistItem>,
    fetchedAt: Instant = Instant.EPOCH,
): YouTubePlaylistWithItems = object : YouTubePlaylistWithItems {
    override val playlist: YouTubePlaylist
        get() = playlist(playlistId)
    override val items: List<YouTubePlaylistItem> = newItems
    override val maxAge: Duration = maxAge
    override val fetchedAt: Instant = fetchedAt
}

private fun playlistItem(
    playlistId: YouTubePlaylist.Id,
    itemId: YouTubePlaylistItem.Id,
    publishedAt: Instant = Instant.EPOCH,
): YouTubePlaylistItem = YouTubePlaylistItemEntity(
    playlistId = playlistId,
    id = itemId,
    title = "",
    thumbnailUrl = "",
    description = "",
    channel = YouTubeChannelEntity(
        id = YouTubeChannel.Id("channel"),
        title = "channel",
        iconUrl = "",
    ),
    videoId = YouTubeVideo.Id("video"),
    videoOwnerChannelId = null,
    publishedAt = publishedAt,
)
