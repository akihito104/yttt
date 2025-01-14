package com.freshdigitable.yttt.data.source.local

import com.freshdigitable.yttt.data.model.YouTubeChannel
import com.freshdigitable.yttt.data.model.YouTubeChannelEntity
import com.freshdigitable.yttt.data.model.YouTubePlaylist
import com.freshdigitable.yttt.data.model.YouTubePlaylistItem
import com.freshdigitable.yttt.data.model.YouTubePlaylistItemEntity
import com.freshdigitable.yttt.data.model.YouTubeVideo
import com.freshdigitable.yttt.data.source.local.YouTubePlaylistUpdatable.Companion.MAX_AGE_DEFAULT
import com.freshdigitable.yttt.data.source.local.YouTubePlaylistUpdatable.Companion.MAX_AGE_MAX
import com.freshdigitable.yttt.data.source.local.YouTubePlaylistUpdatable.Companion.RECENTLY_BOARDER
import com.freshdigitable.yttt.data.source.local.db.YouTubePlaylistTable
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(Enclosed::class)
class YouTubePlaylistUpdatableTest {
    class Init {
        private val playlistId = YouTubePlaylist.Id("playlist")

        @Test
        fun nullOrEmpty_newItemIsNull_returnsMaxDuration() {
            // setup
            val sut = YouTubePlaylistUpdatable.nullOrEmpty(
                playlistId,
                null,
                Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_MAX)
        }

        @Test
        fun nullOrEmpty_newItemIsEmpty_returnsMaxDuration() {
            // setup
            val sut = YouTubePlaylistUpdatable.nullOrEmpty(
                playlistId,
                emptyList(),
                Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_MAX)
        }

        @Test
        fun init_playlistIsNotCached_returnsDefaultDuration() {
            // setup
            val sut = YouTubePlaylistUpdatable(
                playlistId = playlistId,
                playlist = null,
                newItems = listOf(
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_01"),
                    ),
                ),
                fetchedAt = Instant.EPOCH,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_DEFAULT)
        }

        @Test
        fun init_playlistItemsIsModified_returnsDefaultDuration() {
            // setup
            val sut = YouTubePlaylistUpdatable(
                playlistId = playlistId,
                playlist = YouTubePlaylistTable(
                    id = playlistId,
                ),
                cachedItems = listOf(
                    playlistItem(
                        playlistId = playlistId,
                        itemId = YouTubePlaylistItem.Id("item_id_01")
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
            assertThat(sut.maxAge).isEqualTo(MAX_AGE_DEFAULT)
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

        private fun playlist(maxAge: Duration): YouTubePlaylist = YouTubePlaylistTable(
            id = playlistId,
            lastModified = Instant.ofEpochMilli(12000) + RECENTLY_BOARDER,
            maxAge = maxAge,
        )

        @Test
        fun playlistOfActiveAccount_returnsTwiceOfMaxAge() {
            // setup
            val maxAge = Duration.ofMinutes(10)
            val sut = YouTubePlaylistUpdatable(
                playlistId = playlistId,
                playlist = playlist(maxAge = maxAge),
                cachedItems = newItems,
                newItems = newItems,
                fetchedAt = Instant.ofEpochMilli(20000 - 1) + RECENTLY_BOARDER,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(maxAge.multipliedBy(2))
        }

        @Test
        fun playlistOfActiveAccount_returnsUpperLimitDuration() {
            // setup
            val maxAge = Duration.ofMinutes(20)
            val sut = YouTubePlaylistUpdatable(
                playlistId = playlistId,
                playlist = playlist(maxAge = maxAge),
                cachedItems = newItems,
                newItems = newItems,
                fetchedAt = Instant.ofEpochMilli(20000 - 1) + RECENTLY_BOARDER,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(Duration.ofMinutes(30))
        }

        @Test
        fun playlistOfNotActiveAccount_returnsTwiceOfCurrentMaxAge() {
            // setup
            val maxAge = Duration.ofMinutes(20)
            val sut = YouTubePlaylistUpdatable(
                playlistId = playlistId,
                playlist = playlist(maxAge = maxAge),
                cachedItems = newItems,
                newItems = newItems,
                fetchedAt = Instant.ofEpochMilli(20001) + RECENTLY_BOARDER,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(Duration.ofMinutes(40))
        }

        @Test
        fun playlistOfNotActiveAccount_returnsUpperLimitDuration() {
            // setup
            val maxAge = Duration.ofHours(15)
            val sut = YouTubePlaylistUpdatable(
                playlistId = playlistId,
                playlist = playlist(maxAge = maxAge),
                cachedItems = newItems,
                newItems = newItems,
                fetchedAt = Instant.ofEpochMilli(20001) + RECENTLY_BOARDER,
            )
            // verify
            assertThat(sut.maxAge).isEqualTo(Duration.ofDays(1))
        }
    }
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
