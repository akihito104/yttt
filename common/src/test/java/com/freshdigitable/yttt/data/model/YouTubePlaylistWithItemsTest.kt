package com.freshdigitable.yttt.data.model

import com.freshdigitable.yttt.data.model.YouTubePlaylistWithItems.Companion.update
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(Enclosed::class)
class YouTubePlaylistWithItemsTest {
    class Init {
        private val playlistId = YouTubePlaylist.Id("playlist")

        @Test
        fun create_newItemIsNull_returnsMaxDuration() {
            // setup
            val sut = YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist(playlistId),
                items = null,
                fetchedAt = Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(YouTubePlaylistWithItems.MAX_AGE_MAX)
        }

        @Test
        fun create_newItemIsEmpty_returnsMaxDuration() {
            // setup
            val sut = YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist(playlistId),
                items = emptyList(),
                fetchedAt = Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(YouTubePlaylistWithItems.MAX_AGE_MAX)
        }

        @Test
        fun create_newItemIsNotEmpty_returnsDefaultDuration() {
            // setup
            val sut = YouTubePlaylistWithItems.newPlaylist(
                playlist = playlist(playlistId),
                items = listOf(
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_01"),
                    ),
                ),
                fetchedAt = Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(YouTubePlaylistWithItems.MAX_AGE_DEFAULT)
        }

        @Test
        fun update_playlistItemsIsModified_returnsDefaultDuration() {
            // setup
            val playlistWithItems = playlistWithItems(
                playlistId = playlistId,
                items = listOf(
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_01")
                    ),
                ),
            )
            val sut = playlistWithItems.update(
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
            assertThat(sut.maxAge).isEqualTo(YouTubePlaylistWithItems.MAX_AGE_DEFAULT)
        }

        @Test
        fun update_playlistItemsIsModifiedWithEmpty_returnsMaxDuration() {
            // setup
            val playlistWithItems = playlistWithItems(
                playlistId = playlistId,
                items = listOf(
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_01")
                    ),
                ),
            )
            val sut = playlistWithItems.update(
                newItems = emptyList(),
                fetchedAt = Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(YouTubePlaylistWithItems.MAX_AGE_MAX)
        }
    }

    class PlaylistIsNotModified {
        private val playlistId = YouTubePlaylist.Id("playlist")
        private val items = listOf(
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
            val sut = playlistWithItems(maxAge, items).update(
                newItems = items,
                fetchedAt = Instant.ofEpochMilli(20000 - 1) + YouTubePlaylistWithItems.RECENTLY_BOARDER,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(maxAge.multipliedBy(2))
        }

        @Test
        fun playlistOfActiveAccount_returnsUpperLimitDuration() {
            // setup
            val maxAge = Duration.ofMinutes(20)
            val sut = playlistWithItems(maxAge, items).update(
                newItems = items,
                fetchedAt = Instant.ofEpochMilli(20000 - 1) + YouTubePlaylistWithItems.RECENTLY_BOARDER,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(Duration.ofMinutes(30))
        }

        @Test
        fun playlistOfNotActiveAccount_returnsTwiceOfCurrentMaxAge() {
            // setup
            val maxAge = Duration.ofMinutes(20)
            val sut = playlistWithItems(maxAge, items).update(
                newItems = items,
                fetchedAt = Instant.ofEpochMilli(20001) + YouTubePlaylistWithItems.RECENTLY_BOARDER,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(Duration.ofMinutes(40))
        }

        @Test
        fun playlistOfNotActiveAccount_returnsUpperLimitDuration() {
            // setup
            val maxAge = Duration.ofHours(15)
            val sut = playlistWithItems(maxAge, items).update(
                newItems = items,
                fetchedAt = Instant.ofEpochMilli(20001) + YouTubePlaylistWithItems.RECENTLY_BOARDER,
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
    items: List<YouTubePlaylistItem>,
    fetchedAt: Instant = Instant.EPOCH,
): YouTubePlaylistWithItems = object : YouTubePlaylistWithItems {
    override val playlist: YouTubePlaylist
        get() = playlist(playlistId)
    override val maxAge: Duration = maxAge
    override val items: Collection<YouTubePlaylistItem> = items
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
